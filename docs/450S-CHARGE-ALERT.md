# 450S charge target alert

Ather's [AtherStack 7 compatibility announcement](https://forum.atherenergy.com/t/atherstack-7-features-compatibility-and-more/353028) excludes the 450S from Remote Control, including remote charging stop. The [450 specifications](https://www.atherenergy.com/450/specifications) also exclude it from Optimised Charging. Checked 10 October 2026.

The supervised 450S test returned HTTP 200, followed by a remote charging report with state 4 and error 5. The rider confirmed charging continued. The precise meaning of those numeric codes remains unknown.

For the detected 450S (or the saved 450S selection when detection is unavailable), the charging screen offers **Notify at** instead of automatic cutoff. Pause and Resume are hidden and blocked in the repository. Remote commands also wait for vehicle profile lookup, preventing an old limit from dispatching before the model is known.

Existing 450S automatic limits are disabled during migration, preserving the selected percentage. Enable the alert explicitly. The alert uses fresh measured battery and charging evidence, never a predicted ETA. It fires once at or above the target, including when first enabled above the target. The latch is saved per scooter and rearms after a fresh unplugged reading or a deliberate target change/off-on cycle. Repeated or stale readings do not alert. A missed unplugged reading can prevent rearming; turn the alert off and on for the next charge in that case.

Allow notifications for Athr+ and its **Charging Alerts** channel in Android settings. Keep charging monitoring active and the phone online. Delayed cloud telemetry, lost connectivity, disabled notifications, or Android stopping the app can delay or prevent an alert. **An alert never stops charging. Unplug manually.** No additional live scooter commands were used to develop this change.

## Phone verification

1. Install the personal build, sign in locally, and check that the scooter is detected as 450S.
2. Confirm any old limit is off, the card says Charge target alert, and Pause/Resume are absent.
3. Enable notifications and choose a target slightly above the current battery level while supervising the charger.
4. Confirm one alert arrives after a fresh reading reaches the target, charging continues, and subsequent readings do not duplicate the alert.
5. Restart the app and confirm the reached-target latch is retained. Unplug, allow a fresh disconnected reading, and check the next supervised session rearms.

These phone checks are still required; desktop unit tests cannot verify Android notification delivery or background survival.

## Desktop verification

The Android sources compiled and all 213 JVM unit tests passed, including target threshold, stale/invalid readings, duplicate suppression, fresh-unplug rearming, legacy-setting migration, widget/notification wording, and repository-level command blocking. Command: `./gradlew :app:testDebugUnitTest -x buildRust` with Java 17 and Android SDK 34. The native Rust build was excluded; no installable APK or on-phone notification test was produced by this check.

## Test APK

The debug build installs as **Athr+ 450S Test** (`io.ather.pro.test450s`), alongside the original app. It has separate settings and requires a fresh local login. Its upstream update check is disabled. GitHub's test workflow runs the unit tests, builds all four native Android architectures, and uploads the APK as the `athr-plus-450s-test` artifact. This is a debug-signed test build, not a publisher-signed release; different CI builds may have different signing keys and require uninstalling the earlier test app before installation.

## Charging visibility update (11 October 2026)

The rider confirmed the first target notification was received on the phone. The next test build separates the cloud connection from battery and charging report ages on the dashboard and in the ongoing notification. GPS packets cannot make these reports look fresh. Missing and future timestamps are explicitly labelled.

Expand **Alert history** on the charging card to inspect the last 40 local events for the selected scooter. Battery reports are sampled at most once every five minutes, with duplicate source timestamps omitted. Target changes, target reached, and Android notification submission outcomes are recorded separately. History contains only timestamps, percentages, and fixed event types, and survives restarting the app and disabling the alert. It contains no login tokens, phone numbers, VINs, or GPS coordinates. A successful notification submission does not prove visible delivery; Android settings and background restrictions still matter.

Bluetooth navigation and display experiments have not started.

The local 1.1.18-450s-test build (version code 21) passed all 220 JVM unit tests and APK signature verification. It uses the same signing certificate as the local 1.1.17 test APK, allowing an update without uninstalling. All four unchanged native libraries were reused and checked against the previous APK; Rust was not rebuilt.

For this update, check the separate report ages on Home and the ongoing notification, expand Alert history after a supervised target alert, and restart the app to check the history remains. These new UI and persistence checks still need phone verification.
