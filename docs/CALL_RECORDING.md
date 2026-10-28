# Call recording on Android: what is and isn't possible

This app records calls only where Android officially allows it, uses no accessibility-service
or undocumented workarounds, and reports what was actually captured.

## Platform rules (Android 10 / API 29 and later)

| Capture path | Who may use it | What this app does |
|---|---|---|
| `AudioSource.VOICE_CALL`, `VOICE_UPLINK`, `VOICE_DOWNLINK` (both sides of the call from the telephony path) | Apps holding `android.permission.CAPTURE_AUDIO_OUTPUT`, a `signature\|privileged` permission. In practice only apps the manufacturer pre-installs with a privapp-permissions allow-list entry. | Requests the permission in the manifest. If it is granted (privileged install), records `VOICE_CALL`. A normal install never gets it. |
| Microphone (`AudioSource.MIC`) during a voice call | Android's concurrent-capture policy: while a voice call is active, "the call always receives audio". An ordinary app can capture audio only if it is an accessibility service, and can capture the voice call only if it is privileged with `CAPTURE_AUDIO_OUTPUT`. Other apps get **silence**. | Tries the microphone, labelled "mic only", and watches `AudioRecordingConfiguration.isClientSilenced()` and the real signal level. Silent recordings are labelled as silent and never presented as successful. |
| Accessibility-service capture | Technically possible, but it's the workaround the spec rules out and Google Play restricts it. | Not used. |
| Default-dialer role (`ROLE_DIALER`) | Any app the user chooses. | Gives the in-call UI, call-log access and incoming-call handling. It does **not** grant call-audio capture: the Android docs list no default-dialer exemption. |

Source: [Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input)
("Voice call + ordinary app") and [Concurrent capture (AOSP)](https://source.android.com/docs/core/audio/concurrent).

### What to expect on a stock phone with a normal install

* **Recording is offered as "Record (mic)"**, and Settings explains why.
* On most devices running Android 10+, a mic recording made during a cellular call will be
  **silent**. The app detects that, stores the file labelled "Silent: blocked by Android", and
  tells you so when you stop.
* Where an OEM's audio policy does deliver microphone audio, you'll get your own voice, plus the
  other person only if the speakerphone is on. These recordings are labelled "Microphone only".
* Two-way recording works only if the app is installed as a privileged system app (custom ROM or
  OEM build with a privapp-permissions allow-list entry for `CAPTURE_AUDIO_OUTPUT`). Even then,
  the HAL must support `VOICE_CALL` capture. If it refuses, the app falls back to the microphone
  and labels the recording accordingly.

I haven't been able to test these behaviours on hardware yet. The rules above come from the
Android documentation. The detection code (`isClientSilenced`, signal level) is what makes the
app truthful on devices that behave differently.

## Compliant alternatives

1. **The manufacturer's own Phone app.** Samsung, Xiaomi and others ship privileged dialers
   with built-in recording in some regions, and the Google Phone app does in some countries.
   Settings shows a hint for recognised manufacturers. Availability depends on region and
   firmware, so the app doesn't promise it.
2. **Speakerphone + microphone** on devices that don't silence the mic. Quality is limited.
3. **A privileged install** (custom ROM / enterprise device), as described above.
4. **An external recorder** connected to the phone's audio path.

## Device-specific dependencies

| Dependency | Effect |
|---|---|
| Android version | API 29+ required. `CallAudioState`/`setAudioRoute` are deprecated in API 34 but still work; Bluetooth device names need `BLUETOOTH_CONNECT` (not requested), so only "Bluetooth" is shown. |
| Manufacturer audio policy / HAL | Decides whether mic capture during a call is silenced, and whether `VOICE_CALL` works for privileged apps. |
| Region / carrier | OEM dialers enable or disable recording per region; some carriers play an announcement. |
| Default-dialer role | Without it, Telecom uses another app's in-call UI and the recording button isn't reachable. `READ_CALL_LOG` is a restricted permission that Android normally only lets the default phone app hold. |
| Full-screen intent (API 34+) | `USE_FULL_SCREEN_INTENT` is granted to calling apps. If it's revoked, incoming calls show as heads-up notifications only. |
| Notification permission (API 33+) | Needed for the incoming-call notification and the recording notification. Without it the app opens the in-call screen directly. |
| Proximity sensor | Used to turn the screen off at the ear. Skipped if absent. |

## Consent and indicators

There is no extra consent dialog: recording starts when you tap **Record**. It is never silent:

* a pulsing **REC** indicator with elapsed time shows on the in-call screen,
* a foreground-service notification ("● Recording", with a Stop action) shows for as long
  as capture runs, and the app never restarts capture on its own (`START_NOT_STICKY`),
* Android's own microphone privacy indicator (Android 12+) is untouched.

**Legal note:** recording laws differ by country and state, and some require every party's
consent. Telling the other person, where the law requires it, is the user's responsibility.

## Storage and privacy

* Audio: AAC-LC, 16 kHz mono, 32 kbit/s (about 14 MB per hour), in ADTS framing so that an
  interrupted recording can be recovered up to the last whole frame.
* Encryption: Google Tink `AES256_GCM_HKDF_4KB` streaming AEAD, with the keyset wrapped by an
  Android Keystore key. Each file's associated data is bound to its recording id. Metadata (number,
  name, timestamps) is encrypted the same way. Nothing is written in plaintext, and playback
  decrypts on the fly through a `MediaDataSource`.
* Location: `noBackupFilesDir/recordings`. Backups and device-to-device transfer are disabled,
  so recordings are lost on uninstall or factory reset.
* The app has **no INTERNET permission**. Sharing asks for confirmation, then writes a
  decrypted copy to the cache for the chosen app. Copies are cleared on the next launch and from
  Settings > Storage.
