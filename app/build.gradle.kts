import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------------------------
// Version — single source of truth lives in /version.properties (bumped by the Release workflow)
// ---------------------------------------------------------------------------------------------
val versionProps = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE").toInt()

// ---------------------------------------------------------------------------------------------
// Constant release keystore
//
// Every release MUST be signed with the same keystore, otherwise Android refuses to install the
// update over the existing app. The keystore is NEVER generated on the fly:
//   * CI:    decoded from the encrypted GitHub secret MT_KEYSTORE_BASE64 (see release.yml)
//   * Local: referenced from the git-ignored file /keystore.properties (see keystore.properties.example)
// If neither is present, release builds are produced UNSIGNED instead of with a random key.
// ---------------------------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------------------------
// In-app updater configuration (GitHub Releases)
// ---------------------------------------------------------------------------------------------
val updateRepoOwner = secret("MT_UPDATE_REPO_OWNER", "updateRepoOwner") ?: "zihadexplor"
val updateRepoName = secret("MT_UPDATE_REPO_NAME", "updateRepoName") ?: "MT-Player-apk"

// Optional read-only token so the updater keeps working once the repository becomes private.
// It is injected at build time only (never committed) and lightly obfuscated in the binary.
val updaterToken = secret("MT_UPDATER_TOKEN", "updaterToken") ?: ""
val obfuscationKey = (appVersionCode * 31 + 0x5A) and 0xFF
val obfuscatedToken = updaterToken.toByteArray(Charsets.UTF_8)
  .joinToString(",") { ((it.toInt() xor obfuscationKey) and 0xFF).toString() }

android {
  namespace = "com.example"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.aistudio.mtplayer.kmzv"
    minSdk = 28 // Android 9 (Pie)
    targetSdk = 37 // Android 17
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "UPDATE_REPO_OWNER", "\"$updateRepoOwner\"")
    buildConfigField("String", "UPDATE_REPO_NAME", "\"$updateRepoName\"")
    buildConfigField("int[]", "UPDATER_TOKEN_BYTES", "new int[]{$obfuscatedToken}")
    buildConfigField("int", "UPDATER_TOKEN_KEY", "$obfuscationKey")
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
        enableV1Signing = true // harmless on API 28+, keeps tooling compatibility
        enableV2Signing = true
        enableV3Signing = true // enables future key rotation
      }
    }
  }

  buildTypes {
    release {
      // R8 code + resource shrinking: ~85% smaller APK (faster update downloads).
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

  testOptions { unitTests { isIncludeAndroidResources = true } }

  packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
  }

  dependenciesInfo {
    // Don't embed the (Google-encrypted) dependency block: we ship via GitHub, not Play.
    includeInApk = false
    includeInBundle = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.media3.common)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
