# Checkpoint Timer

An Android countdown timer that alerts you at fixed points *before* the end. Define a total
duration, add as many checkpoint alerts as you like, and save the whole thing as a reusable
template.

Built with Kotlin, Jetpack Compose, Material 3, Room, and a foreground service.

## Features

- **Template list** — saved timers showing name, total duration, and checkpoint count.
  Tap to start instantly; edit, duplicate, or delete from the overflow menu.
- **Template editor** — name, total duration (h/m/s), end sound, end vibration, and a
  reorderable list of checkpoints, each with its own label, trigger time, sound, vibration,
  and enabled toggle.
- **Timer screen** — large remaining time, circular progress, next checkpoint countdown,
  and Pause / Resume / Reset / Cancel.
- **Alerts** — at each checkpoint and at the end the app plays a sound, optionally vibrates,
  posts a notification, and flashes an in-app banner when the screen is open.
- **Runs in the background** — a foreground service with a partial wakelock keeps the timer
  accurate while the app is backgrounded or the screen is off. The foreground notification
  shows the remaining time and offers Pause/Resume and Cancel.
- **Three sample templates** are seeded on first launch.

## Requirements

- JDK 17
- Android SDK with platform **35** and build-tools **35.0.0**
- Gradle 8.14+ (the wrapper pins 8.14.4)

The repository ships a Nix flake that provides all of the above:

```bash
nix develop          # JDK 17, Gradle, Android SDK 35, emulator + system image, Claude Code
```

## Build

Inside the dev shell:

```bash
gradle assembleDebug
```

or with the wrapper (downloads its own Gradle distribution on first run):

```bash
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release build

Android requires the *same* signing key for every future update of an app, so a release build
must be signed with a keystore you keep. Losing the keystore means the app can never be updated
on an existing install — only uninstalled and reinstalled.

Create one (choose your own password; back the file up somewhere safe):

```bash
keytool -genkeypair -v \
  -keystore ~/checkpoint-timer-release.jks \
  -alias checkpointtimer \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then put the details in `keystore.properties` at the repo root — it is gitignored, along with
`*.jks` and `*.keystore`:

```properties
storeFile=/home/you/checkpoint-timer-release.jks
storePassword=your-password
keyAlias=checkpointtimer
keyPassword=your-password
```

```bash
gradle assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties` the release build still runs, but produces an unsigned
`app-release-unsigned.apk` that cannot be installed. Verify a signed build with:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Note that release builds have `isMinifyEnabled = false`. Turning R8 on would shrink the APK
considerably; it is off here because the app is small and it adds a class of runtime failure
that only shows up in the minified build.

## Test

```bash
gradle testDebugUnitTest
```

The suite covers the timing engine (checkpoint ordering, single-fire semantics, pause/resume
arithmetic, background ticks, reset, progress), the sound-preference encoding, and the
template editor's save path against an in-memory fake DAO (validation, persistence, the
completion signal, and the rejected-save message). It is plain JVM JUnit — no device or
emulator required.

## Run on the emulator

The flake includes the emulator and an `android-35` google_apis x86_64 system image. Inside
`nix develop`:

```bash
avdmanager create avd -n ct-test -k "system-images;android-35;google_apis;x86_64" --force
emulator -avd ct-test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.checkpointtimer/.ui.MainActivity
```

Handy while testing layout:

```bash
adb shell wm size 1080x1920 && adb shell wm density 440   # realistic phone metrics
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton
adb exec-out screencap -p > shot.png
adb logcat -d | grep -iE "checkpointtimer|AndroidRuntime"
```

## Architecture

```
com.example.checkpointtimer
├── CheckpointTimerApp.kt      Application + hand-rolled AppContainer (no DI framework)
├── data/
│   ├── AppDatabase.kt         Room database
│   ├── TemplateEntity.kt      templates + checkpoints tables
│   ├── TemplateDao.kt         queries, incl. transactional upsert
│   ├── TemplateRepository.kt  domain mapping + sample seeding
│   ├── TimerTemplate.kt       domain models, decoupled from Room
│   └── SoundChoice.kt         persisted sound selection
├── timer/
│   ├── TimerEngine.kt         pure timing logic — no Android dependencies
│   ├── TimerService.kt        foreground service that owns the engine
│   ├── TimerState.kt          TimerState + the StateFlow the UI observes
│   ├── TimerNotifications.kt  foreground notification and alerts
│   ├── SoundPlayer.kt         ToneGenerator / Ringtone / Vibrator
│   └── DurationFormat.kt      mm:ss formatting
├── ui/
│   ├── MainActivity.kt        edge-to-edge host, notification permission
│   ├── CheckpointTimerNavHost.kt
│   ├── components/            DurationInput, SoundPicker
│   ├── screens/               list, editor, timer
│   └── theme/
└── viewmodel/                 list / editor / timer ViewModels + StateFlow UI state
```

### How the timing works

`TimerEngine` holds no clock. Every call takes the current `SystemClock.elapsedRealtime()`
reading, and the engine works out what has happened since the previous call. Two consequences
fall out of that design:

- Missing ticks is harmless. The service polls every 100 ms, but a tick that arrives after
  thirty minutes in the background still reports every checkpoint it crossed, in order.
- Wall-clock changes cannot affect a running timer, and pausing simply banks the elapsed time
  so resuming picks up exactly where it left off.

Because the engine takes its clock as a parameter, the tests drive time by hand instead of
sleeping.

`TimerService` publishes snapshots into `TimerStateHolder`, a process-wide `StateFlow`. The UI
observes it and never talks to the service directly, so the timer survives configuration
changes and the app being backgrounded.

## Permissions

| Permission | Why |
| --- | --- |
| `VIBRATE` | Checkpoint and end vibration |
| `WAKE_LOCK` | Keeps timing accurate with the screen off |
| `POST_NOTIFICATIONS` | Checkpoint alerts; requested at first launch on Android 13+ |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | The running timer |

The foreground service declares `foregroundServiceType="specialUse"` with the subtype
`timer`, as required from Android 14.

## Notes and limitations

- **Sounds.** The brief called for `ToneGenerator`, but it can only play a fixed table of system
  tones — neither pitch nor level is adjustable, and the beep needed to be higher and louder.
  `BeepTone` therefore synthesises the samples directly: three 130 ms pulses of a 1200 Hz sine
  at 92% of full scale, with short fades so the speaker does not click. Those properties are
  covered by unit tests. The alarm, notification, and custom-ringtone options still use
  `Ringtone`. Both notification channels are intentionally silent and non-vibrating — the app
  plays its own audio, so channel alerts would double up.
- **Overlap.** If a checkpoint lands on the same tick as the end of the timer, the checkpoint
  is reported in the UI but stays silent, so its sound cannot collide with the end sound.
- **Process death.** A running timer lives in the service and is not persisted. If the system
  kills the app process mid-timer, the timer is lost; the service returns `START_NOT_STICKY`
  rather than resuming with wrong timing.
- **Verified on an emulator, not on hardware.** On an API 35 emulator with 3-button navigation
  the following were exercised end to end: the template list and editor, saving (including the
  rejected-save feedback), window insets against both the navigation bar and the keyboard, and
  a full timer run — foreground service, a checkpoint firing on schedule with its notification,
  the progress ring, and the "Time's up" state. Vibration and actual audible output cannot be
  judged from a headless emulator, so those two remain unverified by ear.
