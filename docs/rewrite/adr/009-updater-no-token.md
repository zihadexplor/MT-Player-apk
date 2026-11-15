# ADR-009: Keep the updater, remove embedded token

## Status
Accepted

## Context
The repository is public. 1.x could embed a GitHub token XOR-obfuscated in `BuildConfig`,
which anyone can extract.

## Decision
Keep the updater because it is how users get new versions. Use only the unauthenticated
public Releases API over HTTPS. Verify before installing: SHA-256 from the release notes,
package name, higher `versionCode`, identical signing certificate. Install through
`PackageInstaller`. Background check at most once per 24 h, user can disable it. Remove
`MT_UPDATER_TOKEN` from the build. This is the only network use in the app.

## Consequences
Rate limit is 60 requests/hour/IP, ample for a daily check. No secret in the APK.
