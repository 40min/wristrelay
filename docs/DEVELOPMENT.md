# Development

Use the committed Gradle wrapper, JDK 17+ and Android SDK platform 37.0. Set ANDROID_HOME or an untracked local.properties. The current pinned toolchain is AGP 9.2.0, Gradle 9.4.1 and built-in Kotlin 2.2.10 with the matching Compose compiler plugin.

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Android Lint is the initial static analysis gate. Detekt and richer Android test infrastructure remain foundation tasks. A successful no-source unit-test task does not mean parser or relay coverage exists.

If `java` is not on `PATH`, set `JAVA_HOME` explicitly (verified with Corretto 24 via SDKMAN: `JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew ...`).

## Working rules

- Keep one app module and manual wiring until concrete complexity justifies more.
- Follow the [decision history](DECISIONS.md), particularly independent notifications and source preservation.
- Use synthetic fixtures and meaningful event-sequence tests as parser/relay behavior is implemented.
- Update docs and Dart acceptance evidence as behavior changes.
- Never commit local SDK paths, signing keys, secrets, personal notifications or captured real message bodies.
- Do not claim hardware acceptance based on compilation or simulated notifications.
- Actual device installation, permission changes and Garmin settings are separate owner actions.

## CI and releases

GitHub Actions builds the debug APK and runs lint/unit checks. Release signing, release artifacts/checksums and public release acceptance are Phase 7 work. No production signing material is included.
