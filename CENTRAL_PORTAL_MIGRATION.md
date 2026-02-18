# Sonatype Central Portal Migration Guide

## Background

The project currently uses sbt-ci-release 1.11.2, which supports the new Sonatype Central Portal. However, the GitHub Actions secrets still contain credentials for the legacy OSSRH endpoint (`s01.oss.sonatype.org`), which is being sunset on June 30, 2025.

## Current Issue

The CI build fails with `java.net.ProtocolException: Server redirected too many times (20)` because:
- sbt-ci-release 1.11.2 defaults to publishing to `central.sonatype.com` (new Central Portal)
- GitHub secrets contain credentials for the old `s01.oss.sonatype.org` endpoint
- This mismatch causes authentication failures that manifest as redirect loops

## Temporary Fix

The `build.sbt` file has been updated to explicitly set:
```scala
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
```

This allows CI builds to continue working with the existing credentials while the migration is prepared.

## Migration Steps (To Be Done)

To fully migrate to the Central Portal, follow these steps:

### 1. Create Central Portal Account
1. Go to https://central.sonatype.com/ and sign up/login
2. Verify your namespace (e.g., `dev.kovstas`)

### 2. Generate User Token
1. Login to https://central.sonatype.com/
2. Click your username in the top right, then "View Account"
3. Click "Generate User Token", then "Ok"
4. Save both the token username and password

### 3. Update GitHub Secrets
Update the following secrets in the repository settings:
- `SONATYPE_USERNAME`: Set to the token username (NOT your account username)
- `SONATYPE_PASSWORD`: Set to the token password (NOT your account password)

The `PGP_PASSPHRASE` and `PGP_SECRET` secrets can remain unchanged.

### 4. Update build.sbt
Once the secrets are updated, remove or update the `sonatypeCredentialHost` setting in `build.sbt`:

```scala
// Option 1: Remove the line entirely (uses the default central.sonatype.com)
// Option 2: Explicitly set it to the new endpoint
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
```

### 5. Test the Migration
1. Push a commit to the main branch to trigger a SNAPSHOT release
2. Verify the build succeeds and artifacts are published
3. If successful, try a full release by pushing a tag

## Resources

- [sbt-ci-release Documentation](https://github.com/sbt/sbt-ci-release)
- [Sonatype Central Portal Guide](https://central.sonatype.org/publish/publish-guide/)
- [Generate Central Portal Token](https://central.sonatype.org/publish/generate-portal-token/)
- [OSSRH Sunset Announcement](https://central.sonatype.org/news/20250326_ossrh_sunset/)
