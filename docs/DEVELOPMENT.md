# Development

Use the committed Gradle wrapper, JDK 17+ and Android SDK platform 37.0. Set ANDROID_HOME or an untracked local.properties. The current pinned toolchain is AGP 9.2.0, Gradle 9.4.1 and built-in Kotlin 2.2.10 with the matching Compose compiler plugin.

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest detekt
```

Android Lint and Detekt (default rule set plus `config/detekt/detekt.yml` deviations; composables exempt from function naming; fix code, don't suppress) are the static analysis gates. Robolectric 4.17 runs Android-bound fixture tests (SDK 34 emulation); JUnit covers pure logic. If the Gradle daemon runs a newer JDK than detekt's analyzer supports, the build pins detekt's `jvmTarget` to 17. A successful no-failure test run does not mean every behavior has coverage: Android-bound publication, listener wiring and device behavior are verified by the on-device acceptance runs recorded in [VALIDATION.md](VALIDATION.md), not by unit tests.

If `java` is not on `PATH`, set `JAVA_HOME` explicitly (verified with Corretto 24 via SDKMAN: `JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew ...`).

## Working rules

- Keep one app module and manual wiring until concrete complexity justifies more.
- Follow the [decision history](DECISIONS.md), particularly independent notifications and default source preservation with scoped ADR-014 opt-in dismissal.
- Use synthetic fixtures and meaningful event-sequence tests as parser/relay behavior is implemented.
- Update docs and Dart acceptance evidence as behavior changes.
- Never commit local SDK paths, signing keys, secrets, personal notifications or captured real message bodies.
- Do not claim hardware acceptance based on compilation or simulated notifications.
- Actual device installation, permission changes and Garmin settings are separate owner actions.

## CI and releases

GitHub Actions runs the same four Gradle tasks shown above. The [release plan](PLAN.md) tracks signing and publication.

## Debug tools

Open **Advanced → Debug** in a debug build. Both tools start off.

- **Start capture** records notification metadata and relay traces, up to 200 events. **Stop capture** leaves the buffer available. Use **Copy all** to export it, or clear it when done.
- **Start Spotify probe** compares notification and media-session fields. Let a track play through an ad and back to music. Look for `other → ad → other` in the same field. `ad` means the field matches “Advertisement”, and `contains-ad` means it contains the word in a longer value. The screen shows five events. **Copy Spotify log** exports up to 256. Copy before stopping, which clears the trace.
- **Send test relay notification** posts a test copy on the relay channel with the current auto-clear setting.

Use synthetic messages and review any exported metadata before sharing.
