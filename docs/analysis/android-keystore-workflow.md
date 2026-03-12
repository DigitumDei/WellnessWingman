# Android Keystore Workflow Analysis

## Overview
- A release-ready Android build must reuse the same signing keystore so published updates remain installable for existing users.
- The current application is built with Gradle/Kotlin Multiplatform, so the signing flow should be expressed in Gradle-based Android release jobs rather than legacy MAUI tooling.
- The goal is to keep the canonical keystore out of source control while making CI releases reproducible.

## Key Constraints and Risks
- App updates break if the signing key changes.
- Keystore material and passwords must never be committed or printed to logs.
- GitHub-hosted runners are ephemeral, so the workflow must reconstruct the keystore at runtime.

## Recommended Approach
1. Keep `*.keystore` and `*.jks` out of the repository.
2. Store the canonical keystore as a base64-encoded GitHub Actions secret plus separate alias and password secrets.
3. Decode the keystore during the release workflow and pass signing properties to the Android Gradle build.
4. Restrict release secret access through a protected GitHub environment.

## Implementation Steps
1. Prepare the keystore on a trusted machine:
   ```bash
   keytool -list -v -keystore your-release.keystore
   base64 your-release.keystore > keystore.b64
   ```
2. Add the following GitHub secrets:
   - `ANDROID_KEYSTORE_B64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
3. Update the release workflow to materialize the keystore before invoking the Android release task.

## Sample GitHub Actions Job Snippet
```yaml
name: android-release

on:
  workflow_dispatch:

jobs:
  build-android:
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Create local.properties
        run: echo "sdk.dir=/usr/local/lib/android/sdk" > local.properties

      - name: Decode signing keystore
        shell: bash
        run: |
          echo "$ANDROID_KEYSTORE_B64" | base64 --decode > "$RUNNER_TEMP/release.keystore"
        env:
          ANDROID_KEYSTORE_B64: ${{ secrets.ANDROID_KEYSTORE_B64 }}

      - name: Build signed Android bundle
        run: ./gradlew :androidApp:bundleRelease
        env:
          ANDROID_KEYSTORE_PATH: ${{ runner.temp }}/release.keystore
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
```

## Security Checklist
- Keep keystores and derived artifacts `.gitignore`d.
- Limit environment access to release maintainers.
- Document rotation separately so a future key change is deliberate and audited.
- Mask any secret-like values if workflow debugging is required.

## Historical Context
Older MAUI-specific signing guidance has been intentionally removed from this document. This file now describes the active Android release model only.
