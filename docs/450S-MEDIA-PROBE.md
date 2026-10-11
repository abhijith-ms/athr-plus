# 450S media display experiment

The rider reports that music controls normally work. Occasionally song text freezes and call notifications stop appearing while track controls still work. Turning the scooter key off and on restores operation, without restarting the official app or dashboard. This is a recovery observation, not evidence locating the fault.

The debug test app includes **Settings → 450S media test**. It publishes an Android [MediaSession](https://developer.android.com/reference/android/media/session/MediaSession) with a looping one-second silent PCM audio resource, song title `ATHR TEST 001`, and artist `450S PROBE`. It requests [audio focus](https://developer.android.com/media/optimize/audio-focus), which may pause another player. The official app and existing scooter pairing remain in place. No direct Bluetooth connection, pairing changes, scooter API command, or new permission is added.

## Parked test

1. Update the current Athr+ 450S Test installation. Pair normally and confirm ordinary music metadata works first.
2. Pause your normal music. Keep the official Ather app available as usual; do not force-stop it for the first test.
3. Open Settings → 450S media test and tap **Start test**. Keep this card visible and the phone unlocked.
4. Check that the card says **Media notification registered with Android** and that an Athr+ music notification appears in the phone notification shade. Tap **Android next** on the card: the title should advance and a `NEXT` callback should appear from **Local Android controller** (or **Media callback** on Android 8). This uses the actual session controller, whereas Next title changes local state directly.
5. Open the scooter's music controls using the usual joystick action. Check whether the dashboard song title matches the phone and the artist becomes `450S PROBE`. Record the actual dashboard result; “Session active on phone” confirms only local activation.
6. Tap **Next title** twice. Check whether the dashboard follows the two title changes. Phone button events are labelled separately from received media callbacks.
7. Use the scooter joystick for next/previous and play/pause. Check the phone event list and dashboard title/playback state. Avoid other headphones, watches, notification buttons, or media controls during this step: Android callbacks identify the controller application on Android 9+, not the physical sender. Commands forwarded through our media-button receiver show as Local Android controller too.
8. Tap **Stop test**, then resume your normal player. Leaving the card, changing tabs, or backgrounding the app releases playback, audio focus, and the session, and removes the media notification. Stop also occurs on audio focus loss, including an incoming call. Events are kept only in memory, up to 20; restarting the test resets them.

If text freezes naturally, compare whether joystick commands still appear on the phone. Record observations before a normal key off/on recovery. Do not manufacture call notifications or change permissions for this experiment.

## What the result means

- Visible test titles demonstrate a usable media metadata path for this phone/scooter setup. They do not establish direct GATT access or independence from the official app.
- Received media commands while pressing the joystick demonstrate forwarding into this session under the controlled test conditions. They do not establish access to arbitrary joystick inputs.
- No title change or no events is inconclusive: the official app or Android may still select another session. Bluetooth traffic capture is a later option if this simple test is inconclusive.

This is not navigation, a call-notification fix, or a freeze workaround. It intentionally has no background service; background survival and a screen-off experiment need a separate implementation if this path proves useful.

Desktop tests check command transitions, paused metadata changes, stopped-session protection, title wraparound, and bounded event history. Android selection, actual dashboard text, joystick delivery, and audio-focus recovery require the phone test.

The local 1.1.19-450s-test APK (version code 22) built successfully with all 222 JVM tests passing. Its signature matches the prior local test APK. The four unchanged Rust libraries were reused and compared with the prior APK; Rust was not rebuilt.

## First phone result and revised probe

The rider's 1.1.19 screenshot shows locally changing titles and no media callbacks; the scooter photo shows its trip screen. Starting the regular phone player restored normal music display. That result does not establish that custom metadata is unsupported, nor does it locate the failure.

The revised probe adds a standard [MediaStyle notification linked to the session](https://developer.android.com/reference/android/app/Notification.MediaStyle), a session launch intent, a one-item queue, and a declared media-button receiver. The receiver forwards only to an already running test and cannot start it in the background. An Android-controller check and notification registration status provide local evidence before another scooter test. This setup was compared with the public [Ather-Nav media service](https://github.com/ghanshyam-pal/Ather-Nav/blob/main/app/src/main/java/com/athernav/app/MediaSessionService.java); that project's comments and compatibility claims are not proof of support on this scooter. No code or APK from it is installed or executed.

If the notification and Android controller work but the scooter still shows no test title or forwards no commands, record the result alongside a working regular-player baseline. Stop changing the probe blindly; inspect a short, local Bluetooth capture or Android media-session diagnostic next. Do not publish a raw phone bugreport or Bluetooth log: either can contain personal information.

The revised local 1.1.20-450s-test APK (version code 23) compiled and passed all 223 JVM tests. APK signature verification confirms it can update the previous local test build. The receiver is declared as non-exported in the merged manifest. All four unchanged native libraries match the previous APK; physical notification, callback, and scooter checks remain pending.
