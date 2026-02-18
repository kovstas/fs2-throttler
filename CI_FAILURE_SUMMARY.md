# CI Failure Investigation Summary

## Issue
GitHub Actions workflow run https://github.com/kovstas/fs2-throttler/actions/runs/22147909916/job/64030751191 failed after upgrading sbt-ci-release from 1.9.3 to 1.11.2.

## Root Cause
The upgrade to sbt-ci-release 1.11.2 inadvertently broke the CI build due to incomplete migration to the new Sonatype Central Portal:

1. **What Changed**: The commit removed the line `ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"` from build.sbt, assuming the default would work correctly.

2. **Why It Failed**: 
   - sbt-ci-release 1.11.2 defaults to `central.sonatype.com` (the new Central Portal)
   - GitHub Actions secrets still contain credentials for `s01.oss.sonatype.org` (legacy endpoint)
   - This mismatch caused authentication failures that manifested as redirect loops: `java.net.ProtocolException: Server redirected too many times (20)`

3. **The Underlying Migration**: Sonatype is sunsetting the legacy OSSRH endpoint (including `s01.oss.sonatype.org`) on June 30, 2025. The new Central Portal requires:
   - Different endpoint: `central.sonatype.com` instead of `s01.oss.sonatype.org`
   - User tokens instead of account username/password for authentication
   - Generated from https://central.sonatype.com/ instead of legacy OSSRH

## Solution Implemented

### Immediate Fix (Restore CI)
- Restored `ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"` in build.sbt
- Added detailed comments explaining the situation
- This allows CI to work with existing credentials while migration is prepared

### Migration Guide
Created `CENTRAL_PORTAL_MIGRATION.md` with step-by-step instructions for:
- Creating Central Portal account
- Generating user tokens
- Updating GitHub secrets
- Completing the migration

## Actions Required by Repository Owner

1. **Immediate**: Merge this PR to restore CI functionality
2. **Before June 30, 2025**: Follow the migration guide to:
   - Create/verify Central Portal account
   - Generate user tokens
   - Update GitHub secrets
   - Remove the `sonatypeCredentialHost` line from build.sbt

## Testing

The fix should be validated by:
1. Merging this PR and waiting for CI to run on main branch
2. CI should successfully publish SNAPSHOT artifacts to `s01.oss.sonatype.org`
3. After completing the migration guide, test with a new commit to verify Central Portal publishing works

## References

- [sbt-ci-release Documentation](https://github.com/sbt/sbt-ci-release)
- [Sonatype Central Portal Migration](https://central.sonatype.org/publish/publish-guide/)
- [OSSRH Sunset Announcement](https://central.sonatype.org/news/20250326_ossrh_sunset/)
