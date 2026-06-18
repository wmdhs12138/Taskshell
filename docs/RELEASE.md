# Release Guide

This guide explains how to build a signed release APK for Taskshell.

## 1. Generate a release keystore

Run:

```bash
./scripts/generate-release-keystore.sh
```

This creates:

```text
keystore/taskshell-release.jks
signing.env.example
```

Copy the example file:

```bash
cp signing.env.example signing.env
```

Edit `signing.env` and fill the real passwords.

> Do not commit `signing.env` or `keystore/*.jks`.

## 2. Load signing environment

```bash
source signing.env
```

Expected variables:

```bash
TASKSHELL_KEYSTORE
TASKSHELL_KEY_ALIAS
TASKSHELL_STORE_PASSWORD
TASKSHELL_KEY_PASSWORD
```

## 3. Build release APK

```bash
gradle assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

If signing variables are missing, the release APK may be unsigned or the build may fail depending on the Android Gradle Plugin behavior.

## 4. Verify signature

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

You can also inspect package info:

```bash
aapt dump badging app/build/outputs/apk/release/app-release.apk
```

## 5. GitHub Actions release build

The repository includes `.github/workflows/android.yml`.

For signed release builds, add these repository secrets in GitHub:

```text
TASKSHELL_KEYSTORE_BASE64
TASKSHELL_KEY_ALIAS
TASKSHELL_STORE_PASSWORD
TASKSHELL_KEY_PASSWORD
```

Create the base64 keystore secret locally:

```bash
base64 -w 0 keystore/taskshell-release.jks
```

Use the output as `TASKSHELL_KEYSTORE_BASE64`.

The workflow behavior is:

- push / pull request on `main` or `master`: build debug APK;
- manual workflow dispatch: build signed release APK and upload it as an Actions artifact;
- tag `v*`: build signed release APK, create/update a GitHub Release, and upload the APK as a release asset.

Suggested release tag:

```text
v1.0.0
```

Suggested release asset:

```text
app-release.apk
```

To publish a release, push a version tag after configuring the required secrets:

```bash
git tag -a v1.0.0 -m "Taskshell v1.0.0"
git push origin v1.0.0
```

Do not upload the keystore or passwords as release assets or commit them to Git.
