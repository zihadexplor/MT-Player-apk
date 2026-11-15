# Development environment

## Toolchain

| Tool | Version |
|---|---|
| JDK | 21 (Temurin). Bytecode target is Java 17. |
| Android SDK | `platform-tools`, `platforms;android-36`, `build-tools;36.0.0` |
| Gradle | Use the wrapper only (`./gradlew`). Version is pinned with a checksum in `gradle/wrapper/gradle-wrapper.properties`. |

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties   # git-ignored

./gradlew assembleDebug          # debug APK (applicationId suffix .debug)
./gradlew testDebugUnitTest      # JVM + Robolectric tests
./gradlew lintDebug
```

## Behind a TLS-intercepting proxy

If `sdkmanager` or Gradle fails with `PKIX path building failed`, import the proxy root
certificates into the JDK trust store:

```bash
for f in /usr/local/share/ca-certificates/*.crt; do
  keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$f" .crt)" -file "$f"
done
```

## Release signing

Release APKs are signed only in CI with the constant release keystore stored in GitHub
secrets. Never create a new keystore and never commit keystore files or passwords; see the
README "Signing" section. Local release builds without credentials are produced unsigned.

## Branching

- The rewrite lives on `rewrite/v2`. One commit series per phase, pushed after the phase
  builds and its tests pass.
- Conventional Commit messages drive the version bump in `release.yml`.
