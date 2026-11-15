# ADR-001: Rewrite in place, keep application id and signing

## Status
Accepted

## Context
v1.3.0 is installed by real users and updates through the in-app updater and GitHub
Releases. Android only accepts an update with the same `applicationId` and signing key and
a higher `versionCode`.

## Decision
Delete the old `app/src` tree and write a new one in the same Gradle module. Keep
`applicationId = com.aistudio.mtplayer.kmzv`, the CI keystore secrets, the certificate
fingerprint check and `version.properties`. Change only the internal Kotlin `namespace`
to `com.mtplayer.app`.

## Alternatives considered
- New repository / new app id: clean, but every user would have to reinstall and lose data.
- Incremental refactor: keeps the broken architecture (raw player singleton, overlay video).

## Consequences
- Positive: seamless update, user data importable.
- Negative: component class names change, so pinned shortcuts/widgets from 1.x (none exist)
  would break. Launcher icon keeps working because the launcher activity alias is stable.
