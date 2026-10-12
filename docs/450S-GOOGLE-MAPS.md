# Google Maps display — first live test

The debug test build adds **Settings → Google Maps on 450S**. It reads Google Maps' ongoing notification and publishes a short supported English instruction through the same media session that the rider verified with sample directions and joystick controls. It uses the official app's existing scooter pairing, with no direct BLE connection or scooter-control API command.

## Permission and privacy

Android [notification-listener access](https://developer.android.com/reference/android/service/notification/NotificationListenerService) grants access to all notifications. The UI explains this before opening Android settings. The listener discards other packages before reading extras and processes ongoing `com.google.android.apps.maps` notifications only while the rider has enabled this display. It ignores group summaries. Maps source text is bounded, kept in memory, and cleared on Stop, listener disconnection, and navigation end. It is not logged, written to preferences/database, or uploaded by Athr+. The optional **Show latest Maps text** section is for checking the source on the phone; it can contain street names.

The foreground playback service and listener are declared in the debug manifest only. The service is not exported, and Android's `BIND_NOTIFICATION_LISTENER_SERVICE` permission protects the listener. The extra declared permission is `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; no nearby-device, background-location, or new network permission is added.

## What this version supports

- Explicit English left/right, slight/sharp, keep-left/right, straight, U-turn, and numbered roundabout-exit text. It does not infer turns from street names or destination-side text.
- Metric or imperial distance text at the start of a notification field. Units are preserved except sub-kilometre distances, which are converted exactly to metres. If the distance cannot be read confidently or the combined label exceeds 11 characters, only the maneuver is shown. Route ETA distance is not used as turn distance.
- `CHECK MAP` for unrecognized language, icon-only directions, conflicting turns, rerouting, missing/oversized text, listener loss, future cached timestamps, or reports older than two minutes. Unsupported updates replace the previous turn immediately. A five-second local check expires old readings; checking a cached notification does not make it fresh.
- Joystick play/pause controls the display (`PAUSED` when paused). Next/previous never advances a live turn: only a new Maps report can change it.

These parsing rules use synthetic JVM fixtures. Google Maps does not provide a stable notification-text contract for this integration, and this phone's real notification format still needs calibration. Short labels and supported cases do not make this a complete navigation engine. The initial maneuver may be an unsupported compass heading; complex instructions may show `CHECK MAP`.

## Lifetime and music

An explicitly started [media playback foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#media) owns the session after leaving Settings or locking the phone. The existing silent audio player holds a playback wake lock while playing. It tolerates ducking for Maps speech and pauses/resumes audio for temporary focus loss. Permanent [audio-focus loss](https://developer.android.com/media/optimize/audio-focus), such as another music player taking over, stops and releases the display. The notification has a **Stop display** action; the Settings button and sign-out also stop it. It does not restart automatically after process death or phone boot.

Maps stopping/removing the tracked notification ends this service. If Maps removes/replaces its notification during recalculation, the mirror may stop and need a deliberate new start. No automatic restart is attempted. The sample preview stays foreground-only and cannot run alongside the live service. Disposing an inactive sample card cannot release the live service's session.

Media notification ID 1301 and Maps service notification ID 1302 are now separate from charging monitor ID 1201. This also fixes the previous media probe's notification collision with charging monitoring. Charging behavior is unchanged.

## First phone verification

1. Update Athr+ 450S Test over the current installation. Stop the sample preview first. Pair normally and leave the official Ather app connected.
2. Open **Google Maps on 450S → Notification access settings**, read the explanation, then enable **Athr+ Google Maps display** in Android settings. Return to Athr+. Ordinary app notification permission and this special access are separate permissions.
3. While parked, tap **Start Maps display**, then **Open Google Maps** and start a route. Compare the phone's actual instruction with the displayed text. If `CHECK MAP` appears, return while parked and expand **Show latest Maps text**. Share only the relevant direction text if diagnosis is needed; redact street/place names.
4. Lock the phone and check the current direction remains visible. Compare a later Maps update after unlocking or using a supervised route simulation; stationary tests alone cannot establish moving-route updates or arrival behavior.
5. Check a Maps spoken prompt does not permanently stop the mirror. Confirm play/pause shows PAUSED/resumes and that joystick next/previous does not invent the next turn.
6. End navigation in Maps and confirm the mirror stops. Restart deliberately if testing another route. Test **Stop display** from the notification and confirm normal music returns.
7. If Android blocks listener access for a sideloaded app, report the exact system message rather than changing security settings blindly. Do not publish a raw bugreport or notification dump.

Live parsing, screen-off delivery, speech focus handling, notification-removal behavior, charging/media coexistence, and longer Bluetooth stability still need this phone test. JVM tests and a successful APK build do not verify those platform behaviors.

## Desktop verification (12 October 2026)

The local 1.1.22-450s-test APK (version code 25) compiled and passed all 245 JVM tests. Android lint passed with zero errors; its warnings/information are outside the changed media/navigation files. APK signature verification confirms the same certificate as the previous local build. Merged-manifest checks confirm the service type, non-exported services, binding permission, and one notification listener. The only additional uses-permission is foreground media playback. All four unchanged native Rust libraries match the previous APK; Rust was not rebuilt. No live scooter command or route capture was performed during development.
