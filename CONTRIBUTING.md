# Contributing to Stratus

Thanks for wanting to help. Bug reports, ideas and pull requests are all welcome.

## Reporting a bug

Open an [issue](../../issues/new/choose) and pick **Bug report**. The most useful things to include:

- Stratus version (⋯ → About Stratus) and your Android version and phone
- What you did, what you expected, and what happened instead
- For forecast or alert problems: a nearby city or region (not your exact location), and roughly when it happened
- For widget or At a Glance problems: which widget or Smartspacer plugin, and your launcher

Logs help with crashes and background refresh problems. With USB debugging on, run `adb logcat` while it happens and attach the part around the error. Please look through it first and remove anything personal, especially coordinates.

## Building

You need JDK 17+ and the Android SDK (compile SDK 37). On Windows use `.\gradlew.bat`.

```bash
./gradlew assembleDebug
```

Release builds (`./gradlew assembleRelease`) are minified. Without a `keystore.properties` at the project root they're signed with the debug key, so they install directly.

## Tests

```bash
./gradlew testDebugUnitTest
```

There are no instrumented tests. `ScreenshotTest` renders the main screens with Robolectric into `app/build/screens/`, using recorded Open-Meteo responses for New York City from `app/src/test/resources`, so it runs offline. If you change anything visual, run it and look at the PNGs before and after. To render just the detail sheets:

```bash
./gradlew testDebugUnitTest --tests "*ScreenshotTest.detail*"
```

## Pull requests

- Keep each pull request to one change, and say what it fixes or adds and how you checked it (screenshots from `ScreenshotTest`, or the phone or emulator you tried).
- Stratus aims to look and behave like Apple Weather on iPhone. For UI changes, match iOS layout, spacing, gestures and wording. Apple's fonts, SF Symbols, icons and animations can't be copied, so draw your own in the same style.
- Match the surrounding code: Kotlin, Jetpack Compose, the same naming and comment style. Use `IosSheet` for sheets rather than Material's `ModalBottomSheet`, and the `Type.*` text styles with the Inter font.
- Weather data has to come from free sources that need no API key or account. If a service needs a key, it can't be used.
- New libraries need a license compatible with GPL-3.0, and an entry in `ui/About.kt` (plus its license text in `app/src/main/assets/licenses/` if it isn't one already there).
- Screenshots and test data should use generic public places like New York City, never your own location.

## Ground rules

- **Privacy.** Stratus has no server, no accounts and no telemetry. Your locations go only to the weather services that need them to answer a forecast request. Pull requests that add analytics, crash reporting services, ads or tracking won't be merged.
- Stratus is independent and not affiliated with Apple or Google. Keep it that way in names, icons and wording.

## License

Stratus is licensed under the [GNU General Public License v3.0 or later](LICENSE). By sending a contribution you agree that it's licensed the same way.
