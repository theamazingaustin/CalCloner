# CalCloner Releases

APK releases are managed via **GitHub Releases** (not committed directly into this directory).

## Release Process

### Version Naming Convention

Releases follow the scheme: **`MAJOR.MINOR-MM.DD-BUILD`**

| Segment | Description | Example |
|---------|-------------|---------|
| `MAJOR.MINOR` | App feature version | `2.1` |
| `MM.DD` | Month and day of build | `9.15` |
| `BUILD` | Daily build counter, starting at 1 | `1` |

**Full example:** `2.1-9.15-1` - version 2.1, built on September 15, first build that day.

> Note: `versionCode` in `app/build.gradle.kts` must be incremented with each release.
> Use: `MAJOR * 1000 + MINOR * 100 + BUILD_SEQUENCE` (e.g., v2.1 = 2100, v2.1 build 3 = 2103).

---

### Creating a Release

1. **Build the release APK** in Android Studio:
   - `Build -> Generate Signed Bundle / APK -> APK -> Release`
   - Output: `app/release/app-release.apk`

2. **Tag the commit** using the version name:
   ```bash
   git tag -a "v2.1-9.15-1" -m "Release v2.1-9.15-1"
   git push origin "v2.1-9.15-1"
   ```

3. **Create a GitHub Release** on the tag:
   - Go to: https://github.com/theamazingaustin/CalCloner/releases/new
   - Select the tag you just pushed
   - Upload the APK (`app-release.apk`)
   - Attach release notes

The GitHub Actions workflow at `.github/workflows/release.yml`
will **automatically** build and attach the APK when a version tag is pushed.

---

### Installing a Release APK

Download the APK from the [Releases page](https://github.com/theamazingaustin/CalCloner/releases)
and sideload on Android (`Settings -> Install Unknown Apps`).
