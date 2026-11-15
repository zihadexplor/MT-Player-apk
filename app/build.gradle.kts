import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

// Version — single source of truth lives in /version.properties (bumped by the Release workflow).
val versionProps = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE").toInt()

// Constant release keystore (never generated on the fly):
//   * CI:    decoded from the GitHub secret MT_KEYSTORE_BASE64 (see release.yml)
//   * Local: referenced from the git-ignored /keystore.properties
// Without credentials, release builds are produced UNSIGNED instead of with a random key.
val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(envName: String, propName: String): String? =
  System.getenv(envName)?.takeIf { it.isNotBlank() }
    ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("MT_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = secret("MT_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = secret("MT_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = secret("MT_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
  .all { it != null } && rootProject.file(releaseStoreFile!!).exists()

// Public GitHub repository used by the in-app updater. No credentials are ever embedded.
val updateRepoOwner = System.getenv("MT_UPDATE_REPO_OWNER")?.takeIf { it.isNotBlank() } ?: "zihadexplor"
val updateRepoName = System.getenv("MT_UPDATE_REPO_NAME")?.takeIf { it.isNotBlank() } ?: "MT-Player-apk"

android {
  namespace = "com.mtplayer.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Application identity of every published MT Player release. Never change it.
    applicationId = "com.aistudio.mtplayer.kmzv"
    minSdk = 28
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "UPDATE_REPO_OWNER", "\"$updateRepoOwner\"")
    buildConfigField("String", "UPDATE_REPO_NAME", "\"$updateRepoName\"")
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
      buildConfigField("boolean", "AUTO_UPDATE_CHECK", "true")
    }
    debug {
      // Debug builds install side-by-side with the release app and never replace it.
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      buildConfigField("boolean", "AUTO_UPDATE_CHECK", "false")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
    animationsDisabled = true
  }

  sourceSets {
    // Exported Room schemas are test fixtures for migration tests.
    getByName("test").assets.srcDir("$projectDir/schemas")
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }

  packaging {
    resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module", "DebugProbesKt.bin") }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.generateKotlin", "true")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.coil.compose)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.ui)
  implementation(libs.media3.common)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.guava)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.media3.test.utils)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)
}
