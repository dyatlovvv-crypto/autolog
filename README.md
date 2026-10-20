# Autolog

Android journal for car expenses. Kotlin, Jetpack Compose, Room. No ads.

![Autolog](docs/hero.png)

## Build

```bash
./gradlew :app:assembleRelease
```

Version: see `versionName` / `versionCode` in `app/build.gradle.kts`.

## Backup

Settings → **Резервная копия**: save a JSON file or restore from one.
Restore replaces all local data. No Google account required.
