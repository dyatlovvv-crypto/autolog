# Autolog

Android journal for car expenses. Kotlin, Jetpack Compose, Room. No ads.

![Autolog](docs/hero.png)

## Build

```bash
./gradlew :app:assembleRelease
```

Version: see `versionName` / `versionCode` in `app/build.gradle.kts`.

## Google Drive backup (optional)

1. Create a project in Google Cloud Console, enable **Google Drive API**.
2. OAuth consent screen + **Android** OAuth client:
   - package: `ru.autolog.app`
   - SHA-1 (debug): from `keytool -list -v -keystore ~/.android/debug.keystore` (password `android`)
3. Optional: **Web** client ID in `local.properties` for ID token:

```
GOOGLE_WEB_CLIENT_ID=123456789-xxxx.apps.googleusercontent.com
```

Snapshots go to Drive App Data. Linking an account never wipes phone data; restore from Google is always an explicit confirmed action.
