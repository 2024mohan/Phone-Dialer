# Minimal Dialer

A lightweight full-screen phone dialer for Android 10+ (API 29), built with Kotlin, Jetpack
Compose, Material 3 and MVVM. It has an encrypted call-recording library and tells you
plainly what Android lets it record.

> **Read [docs/CALL_RECORDING.md](docs/CALL_RECORDING.md) before relying on recording.**
> On a normal (non-system) install, Android 10+ does not let third-party apps capture call
> audio. This app offers microphone-only recording, detects when Android silences it, and
> labels every recording with what was actually captured.

## Status

| Part | State |
|---|---|
| Pure-Kotlin domain modules (dial input, T9 search, call-state mapping, recording policy, recorder state machine, ADTS framing, signal detection, encrypted store, catalog) | Built and unit-tested on the JVM: **56 tests pass**, including real Tink encryption round trips. |
| Android modules (UI, Telecom, recording engine, app) | Written, **not yet compiled**: the build container had no access to Google's Maven repository or the Android SDK. Expect a round of compile fixes on the first Android Studio build. |
| On-device behaviour (calls, recording, notifications) | **Not yet tested on hardware.** |

## Features

* Full-screen, edge-to-edge keypad with large circular keys, letters, long-press `0` → `+`,
  `*` → pause, `#` → wait, backspace and long-press clear, DTMF key tones (respects the
  system setting), and T9 / number contact matching while typing.
* Recents (call log), Contacts with search (accent-insensitive), Favourites (starred contacts,
  toggled from the Contacts list).
* Default phone app via `RoleManager.ROLE_DIALER`, `InCallService` integration, incoming-call
  notification with full-screen intent, ongoing-call notification, and showing over the lock screen.
* Call states: dialing, ringing, active, on hold, disconnecting, disconnected (with reason), with
  a live duration. Mute, hold/resume, swap, in-call keypad (DTMF), speaker, Bluetooth / wired
  headset routing, and a proximity screen-off.
* Recording: start/stop during a connected call, REC indicator, foreground notification with
  Stop, auto-stop when the call ends, crash recovery, and silence detection.
* Recording library: grouped by date or contact, sorted by date, duration or size, searchable;
  in-app playback with seeking; rename, delete, share (after confirmation); storage summary;
  delete older than 30/90 days; delete all.
* Dark charcoal / light themes (follow system or override) and minimal animation.
* No INTERNET permission. Backups disabled.

## Architecture

```
app                    Activities, navigation, manual DI (AppContainer), call/recorder glue
core/model        JVM  Contact, RecentCall, phone-number & duration helpers
core/designsystem      Theme, colours, keypad, avatar, REC indicator, permission helpers
dialer/domain     JVM  DialInput editing model, DialKey layout, T9 encoding
dialer                 Keypad screen + DialerViewModel
contacts/domain   JVM  Repository interfaces, ContactSearch (number/T9/text), favourites, recents
contacts               ContactsContract / CallLog repositories, Contacts/Favourites/Recents screens
calling/domain    JVM  CallState, Telecom state & route mapping, CallController interface
calling/telecom        InCallService, TelecomCallController, notifications, role helper
calling/ui             In-call screen + InCallViewModel (depends only on interfaces)
recording/domain  JVM  Capability policy, recorder state machine, ADTS, SignalMonitor,
                       RecordingStore (encrypted), RecordingLibrary, CallRecorder interface
recording/engine       AudioRecord + MediaCodec capture, Tink cipher, foreground service
recordings/domain JVM  Grouping, sorting, search, storage summary, rename rules
recordings             Library screen, encrypted playback, share
settings               Permissions, recording capability report, storage, privacy, theme
```

The UI modules depend on interfaces (`CallController`, `CallRecorder`, repositories), and
`app` wires in the Telecom and Android implementations. The `JVM` modules have no Android
dependency, so they build and test without the SDK.

## Build

Requirements: Android Studio Meerkat Feature Drop (2024.3.2) or newer or the command-line SDK with platform 35,
JDK 17+.

```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                          # all local unit tests (JVM modules + Android modules)
./gradlew connectedDebugAndroidTest     # instrumentation tests (device or emulator)

# Domain modules only, without the Android SDK:
gradle -p tools/jvm-check test
```

AGP 8.10.1, Kotlin 2.1.21, Compose BOM 2025.06.00, compileSdk/targetSdk 35. The Google-Maven
versions in `gradle/libs.versions.toml` couldn't be checked from the build container. Bump
them together if Android Studio suggests newer ones.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Open **Phone**, then tap **Set** on the banner (or Settings > Default phone app) and choose
   this app. Without the role, Android keeps using the previous dialer's in-call screen.
2. Allow Contacts, Call log, Phone and Notifications when asked.
3. During a call, tap **Record (mic)** and allow the microphone.
4. Check **Settings > Call recording on this device** for what your device allows.

To go back to your previous dialer: system Settings > Apps > Default apps > Phone app.

## Device compatibility

* **Calling UI** should work on any Android 10+ phone with telephony once the app is the default phone app.
* **Recording**: see the table in [docs/CALL_RECORDING.md](docs/CALL_RECORDING.md). In short:
  two-way recording needs a privileged (system) install. Normal installs get microphone-only
  recording, which most devices silence during cellular calls. The app reports this rather
  than hiding it.
* **Not supported**: video calls, conference merge, RTT, voicemail playback, call screening,
  dual-SIM account picker (Telecom shows its own picker), and VoIP / self-managed calls.

## Testing

* `tools/jvm-check`: 56 JVM tests across the domain modules (run in CI or locally without the SDK).
* Android local unit tests: Telecom constant parity (`TelecomConstantsTest`), recording messages
  (never "success" for silent captures), capability wording.
* Instrumentation tests: `DialerScreenTest` (keypad, backspace, T9 suggestion, call placement
  through a fake `CallController`) and `EncryptedStoreOnDeviceTest` (real Android Keystore-backed
  Tink keyset).

## Legal

Laws on recording phone calls vary by jurisdiction, and some require every party's consent. You
are responsible for telling the other party where the law requires it. The app shows a visible
recording indicator and notification and never records without a tap, but it does not notify
the other party.
