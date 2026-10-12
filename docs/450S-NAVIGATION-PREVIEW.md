# 450S navigation preview

This debug-build preview sends manually selected sample directions through the existing Android media session and the scooter's music display. It uses no route, GPS position, or navigation app. The artist is `NAV DEMO` to identify the test. Keep the official Ather app and normal pairing in place.

The six display labels are deliberately limited to 11 uppercase ASCII characters:

| Display text | Meaning shown on phone |
| --- | --- |
| `200M LEFT` | Turn left in 200 metres |
| `50M RIGHT` | Turn right in 50 metres |
| `GO STRAIGHT` | Continue straight |
| `RNDBT EXIT2` | Take exit 2 at the roundabout |
| `1KM LEFT` | Turn left in 1 kilometre |
| `ARRIVED` | You have arrived |

## Parked test

1. Update the existing Athr+ 450S Test app. Open **Settings → 450S navigation preview**. Navigation demo is selected by default; the old Media test remains available when stopped.
2. While parked, tap **Start preview**. Keep the card visible and the phone unlocked. Starting may pause other music. Open the scooter's normal music controls.
3. Check for `200M LEFT`. Use the phone's **Next** and **Previous** buttons to inspect all six samples. Confirm each is readable without waiting for scrolling. After ARRIVED, Next returns to the first sample; Previous from the first returns to ARRIVED.
4. Without pressing phone or notification controls, try the joystick's next/previous actions. Compare the phone sample counter and callback events with each press. Forwarding is wired but still needs this hardware check. Play/pause changes playback, without advancing the sample. No direction advances automatically.
5. If no joystick callback appears, tap **Android next** to check the local session controller, then record which path worked. Android callback labels identify an application, not the physical sending device.
6. Leave one sample displayed for a few minutes, then step forward and backward several times. Note any freeze or Bluetooth disconnect and the phone status at the same time.
7. Tap **Stop test**, then play normal music. Confirm the music title returns. Leaving the card/app or losing audio focus also stops the preview and removes its notification.

Mode changes are blocked while the session is active. Starting or changing modes resets the in-memory event list and begins at the first title. Stopping retains the last observed events until the next start or mode change.

This version does not run with the phone locked or in the background. It is sample data for a stationary test, not guidance for a ride. Directions occupy the music-title field and compete with regular music. Live navigation and notification-listener access will be considered after this display and control check.

JVM tests cover label length and supported character choices, sample cycling and wraparound, pause behavior, callback source labels, stopped-session protection, and blocked mode changes during playback. Real display readability, joystick forwarding, and longer connection stability require phone/scooter verification.

The local 1.1.21-450s-test APK (version code 24) compiled and passed all 226 JVM tests. Its verified signature matches the previous local test APK, and its permissions are unchanged. All four unchanged native libraries match the previous APK; Rust was not rebuilt.

## Phone result (12 October 2026)

The rider supplied photos showing `200M LEFT` and `50M RIGHT` clearly on the 450S display and confirmed that both the scooter joystick and phone buttons worked properly. This verifies sample display updates and media-command forwarding for the tested phone/scooter setup. Longer-session stability and screen-off operation remain unverified. Live navigation has not been implemented; its first instruction source will follow the rider's navigation-app choice.
