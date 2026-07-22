# Repository Guidelines

Single-module Android app: Kotlin + Jetpack Compose, Retrofit/Moshi against
the ElevenLabs API, Media3 for background playback.

## Project Structure & Module Organization

```
app/
  src/main/
    AndroidManifest.xml          # permissions, MainActivity, PlaybackService
    java/com/example/eaa/
      MainActivity.kt            # single Activity, hosts Compose nav between 4 screens
      api/                       # Retrofit interface + DTOs (Voice, SharedVoice, SynthesizeRequest, …)
      audio/                     # Media3 PlaybackService + PlayerHolder (singleton MediaPlayer)
      model/                     # GeneratedItem (library rows)
      ui/                        # shared composables (LibraryRow, VoiceFilters, …)
      ui/screens/                # GeneratorScreen, LibraryScreen, SettingsScreen, CloneVoiceScreen
      util/                      # AudioLibrary, AppSettings, Chunker, ClonedVoicesStore,
                                 # KeychainHelper, RuVoiceCatalog
    res/                         # drawable, mipmap-*, values (strings/themes), values-night
  src/test/                      # (empty — no unit tests yet)
  src/androidTest/               # (empty — no instrumented tests yet)
  signing/debug.keystore         # committed so CI can sign release APK
.github/workflows/android.yml    # CI: JDK 17 + Android SDK 34, ./gradlew assembleRelease
build.gradle, settings.gradle, gradle.properties, gradlew
```

Naming convention: package `com.example.eaa`, one Composable per top-level
screen file, helpers private to the file unless reused (then move to `ui/`
or `util/`).

## Build, Test, and Development Commands

CI in `.github/workflows/android.yml` is the source of truth for builds.
On a host with JDK 17 + Android SDK 34 installed:

```bash
./gradlew assembleRelease      # release APK → app/build/outputs/apk/release/
./gradlew assembleDebug        # debug APK
./gradlew lint                 # Android Lint
./gradlew test                 # JVM unit tests (none wired yet)
./gradlew connectedAndroidTest # instrumented tests (none wired yet)
```

`app/src/test/` and `app/src/androidTest/` are empty; add a source set
alongside the code you’re testing if you introduce tests.

## Coding Style & Naming Conventions

- Kotlin idiomatic style, 4-space indentation, trailing commas where they
  help diffs.
- Jetpack Compose throughout; prefer `Modifier`-driven layout, hoist state,
  keep composables side-effect-free except via `LaunchedEffect` /
  `DisposableEffect`.
- One top-level `@Composable` per screen file; shared row/widget composables
  live in `ui/`.
- DTOs use `@JsonClass(generateAdapter = true)` with `@Json(name = …)` —
  follow the existing pattern when adding API fields.
- Resource IDs: `ic_*` for drawables, `screen_*` for new strings.

## Commit & Pull Request Guidelines

History shows short, imperative, capitalized subjects with a `Fix: …` /
`Feat: …` prefix and a blank line before the body. The body explains the
*why* and lists per-file changes in bullets. Example:

```
Fix: cloned voice auto-appears in list + preview in Clone tab

CloneVoiceScreen:
- Persist new voice into ClonedVoicesStore on success …
- LaunchedEffect on lastClonedVoiceId auto-plays a short RU phrase …
```

PRs: rebase on `main`, keep the build green (CI runs `assembleRelease`),
include screenshots/screen-recordings for any UI change, and link the
issue you’re closing. New runtime permissions or new foreground-service
usage must be called out in the PR description.

## Security & Configuration

- The ElevenLabs API key is stored in Android Keychain via
  `util/KeychainHelper.kt`; never log it (OkHttp `redactHeader("xi-api-key")`
  is already configured in `MainActivity`).
- `app/signing/debug.keystore` is the committed debug key; replace with a
  real release keystore (already in `.gitignore` as `*.jks`/`*.keystore`)
  before publishing to Play.
- Network calls go to `https://api.elevenlabs.io/v1/`. No additional
  `local.properties` or secrets are required for the app to compile.
