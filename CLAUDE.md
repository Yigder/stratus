# Stratus

Android weather app modelled closely on Apple Weather for iPhone. Kotlin + Jetpack Compose, single `app` module, package `com.weatherclone.app`. Free data sources only — no API keys.

The goal is fidelity to Apple Weather's look and behaviour. When adding or changing UI, match iOS (layout, spacing, gestures, wording) unless something can't legally be copied (SF Pro, SF Symbols, Apple's animations) — then draw our own in the same style.

## Build and test

```bash
./gradlew assembleRelease          # APK -> app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # Robolectric screenshot tests -> app/build/screens/*.png
./gradlew testDebugUnitTest --tests "*ScreenshotTest.detail*"   # a subset
```

- Toolchain: AGP 9.x with built-in Kotlin, Kotlin 2.4 (compose + serialization plugins), Gradle 9.x, JDK 17+.
- compileSdk 37, targetSdk 36, minSdk 29 (raised for the Smartspacer SDK).
- Release builds are minified and resource-shrunk. Signing comes from a git-ignored `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it, release falls back to the debug key. Never commit keystores or that file.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for each release.

There are no instrumented tests. Verify visual changes by rendering screenshots in `ScreenshotTest` and looking at the PNGs. Test fixtures in `app/src/test/resources` are real API responses for New York City; keep any new fixtures and screenshots to generic public locations.

## Layout

```
data/        Models, parsing, repository, alerts, astronomy, unit formatting, summaries
ui/          Compose screens and drawing
glance/      Home-screen widgets (Glance) and the Smartspacer plugin
background/  WorkManager refresh and notifications
```

Key files:
- `data/Repository.kt` — `WeatherRepository.get(context)` singleton; fetches and caches forecast/air/alerts/normals as files; `Store` wraps SharedPreferences (places, settings, notification prefs).
- `data/Parse.kt` — Open-Meteo JSON -> `Weather`. `data/Alerts.kt` — NWS (US), ECCC (Canada), MeteoAlarm (38 European countries), chosen by `sourceFor(place)`.
- `data/Summaries.kt` — the plain-English sentences on cards and detail sheets.
- `ui/WeatherApp.kt` — top-level Pager / List / Map screens and the blurred bottom bar.
- `ui/LocationPage.kt` — one location's forecast page; collapsing header; provides `LocalHaze` for card blur.
- `ui/Cards.kt` + `ui/Common.kt` — the cards; `WeatherCard` handles the sticky-header collapse under the title.
- `ui/Detail.kt` — detail sheets (charts, Sun, Moon, Averages, Alerts), chart scrubbing, day swiping.
- `ui/Sheet.kt` — `IosSheet`, the custom iOS-style page sheet used instead of Material's `ModalBottomSheet`.
- `ui/Sky.kt` — `Scene` (colors per condition/time) and `drawSky(scene, t)` animated backgrounds.
- `ui/Icons.kt` — hand-drawn multicolor weather icons (`drawWx`).
- `ui/MapView.kt` + `ui/MapLayers.kt` — tile map, radar, forecast grid layers, timeline slider.
- `ui/Render.kt` — renders Compose drawing to bitmaps for widgets and Smartspacer.

## Data sources

Open-Meteo (forecast, air quality, archive normals, geocoding, multi-point grids for map layers), RainViewer (radar tiles, past ~2h only), Esri Dark Gray Canvas (base map + labels; the attribution string in `MapScreen` must stay), NWS / ECCC / MeteoAlarm (alerts). CARTO tiles now require a key — don't switch back.

## Conventions and gotchas

- **Font:** Inter variable font (`res/font/inter.ttf`, OFL) via `ui/Fonts.kt`. Use `Type.*` styles or `InterDisplay` for large numbers; don't introduce Roboto-looking text.
- **Blur:** Haze 2.x API — `Modifier.hazeSource(state)` on the background, `Modifier.hazeBlur(input = HazeInput.Backdrop(state), style = HazeBlurStyle { ... })` on the blurred element. Cards get it through `cardMaterial(scene)` in `Common.kt`.
- **Sheets:** use `IosSheet`, not `ModalBottomSheet`. It coordinates drag-to-dismiss with inner scrolling through a `NestedScrollConnection` and disables stretch overscroll; Material's sheet fought with the scroll and stuttered.
- **Gestures in detail sheets:** children see pointer events first. The chart claims a drag only on a clear sideways move or a ~140 ms hold, then consumes; the day-swipe detector on the sheet gives up if an event is already consumed or the drag is vertical. Keep `pointerInput` keys stable (`Unit` + `rememberUpdatedState`) — keying on a value rebuilt each recomposition cancels the gesture mid-drag.
- **Chart scrub state** is a fractional hour read only in the Canvas draw phase, so scrubbing redraws without recomposing; the header reads a `derivedStateOf` rounded index.
- **Animated skies:** `drawSky` must render sensibly at any `t`, including `t = 0` (widgets and list cards draw a still frame). Periodic effects (lightning, shooting stars) are phase-offset so they aren't mid-flash at `t = 0`. `LocalAnimate provides false` in tests.
- **Glance widgets:** a Glance `Column`/`Row` holds at most 10 children — nest containers for longer lists. Use `androidx.glance.action.actionStartActivity`.
- **Smartspacer plugin:** complication + target providers in `glance/Smartspacer.kt`; manifest authorities, permissions and actions must match Smartspacer's exactly. `Sync.refreshSurfaces` updates widgets and notifies Smartspacer after each primary-location refresh. Pixel's At a Glance has no public API; Smartspacer is the only route.
- **Background work:** `Sync.schedule` runs a 15-minute periodic `RefreshWorker`. Robolectric tests use a plain `Application` (see `@Config` in `ScreenshotTest`) because WorkManager isn't initialized there.
- **Constructors with many params** (`Place`, etc.): use named arguments; positional calls broke when fields were added.

## Editing on Windows / PowerShell 5.1

- Prefer the Edit/Write tools for source changes. If a script must rewrite a file, read and write with `[IO.File]::ReadAllText/WriteAllText` and `New-Object Text.UTF8Encoding($false)`. `Get-Content -Raw` reads BOM-less UTF-8 as ANSI and corrupts `°`, `·`, `−`, `μ` (mojibake like `Â°`).
- `R` is an alias for `Invoke-History`; don't name things `R` in inline scripts.
- Don't redirect binary output with `>`. For device screenshots: `adb shell screencap -p /sdcard/s.png` then `adb pull`.
- `adb` may not be on PATH; it lives under the Android SDK's `platform-tools`.

## Privacy

Never commit or publish personal data: no real user locations, device identifiers, local paths, account emails or signing secrets. Screenshots for docs come from `ScreenshotTest` (NYC sample data, no status bar). Commits use the GitHub no-reply address.
