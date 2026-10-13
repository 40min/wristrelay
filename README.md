# WristRelay

**Every message deserves its own place on your wrist.**

WristRelay is an offline Android notification bridge, initially designed for **Telegram → Garmin Connect → Garmin vívoactive 5**.

## Why build this?

Telegram can combine several unread messages into one Android notification. In the behavior that motivated this project, the watch alerts again when that notification changes, but keeps showing the first unread message. The next alert adds little information.

WristRelay aims to turn each genuinely new message into a separate Android notification that Garmin Connect can mirror. Three messages should become three entries you can browse on the watch:

```text
Telegram conversation          WristRelay → watch
  ONE                           ONE
  TWO                           TWO
  THREE                         THREE
```

The goal is to retain all three messages independently. Later arrivals must never overwrite earlier message notifications. Two distinct messages containing “OK” must both arrive; repeated callbacks for the same message must not create duplicates.

## How it will work

```text
Telegram notification snapshots
            ↓
WristRelay: filter → extract → detect new messages
            ↓
One independent Android notification per message
            ↓
Garmin Connect → your watch
```

Telegram keeps its normal phone notifications. In Garmin Connect, users disable forwarding for Telegram and enable it for WristRelay. WristRelay never cancels or modifies Telegram notifications. Its own phone notifications are intended to be silent; channel behavior and watch vibration still require device testing.

## Current status

**Feasibility prototype build is ready — watch acceptance is pending.** The app has a Compose screen with permission controls, an off-by-default task 0.2 prototype relay toggle and a notification-listener service that (a) records metadata-only debug captures (no text content, bounded in-memory) and (b) relays genuinely new Telegram messages as independent notifications from a Telegram-captured, positionally diffed snapshot. Everything phone-side works; whether the watch shows ONE, TWO, THREE as three independently browsable entries is the pending physical gate.

The first milestone is a feasibility APK tested on Pixel 8 and vívoactive 5: the watch must show **ONE, TWO, THREE**, and all three must remain independently browsable. Production relay development depends on that result. Watch compatibility and battery targets are not yet verified.

## Privacy by design

- Local, event-driven processing; no app internet permission, cloud service or telemetry.
- No persistent message history or message-body logging.
- Explicitly enabled source applications only; bounded temporary processing state.
- No root, accessibility service, polling or permanent foreground service.

Android Notification Access grants broad system-level visibility. WristRelay must filter unrelated apps immediately; it cannot make Android's permission Telegram-only. Read the [privacy and security requirements](docs/PRIVACY.md).

## Build

Open the project in Android Studio, or use the Gradle wrapper with JDK 17+ and Android SDK platform 37.0:

```sh
./gradlew assembleDebug
./gradlew lintDebug testDebugUnitTest
```

Set `ANDROID_HOME` to your SDK directory or use an untracked `local.properties`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. No signing keys or machine-specific configuration belong in Git.

The project uses Kotlin, Jetpack Compose, Gradle Kotlin DSL and one Android application module, with provisional minimum Android 10 (API 29). See [architecture](docs/ARCHITECTURE.md) and [development guidance](docs/DEVELOPMENT.md).

## Project documentation

| Document | Purpose |
|---|---|
| [Context](docs/CONTEXT.md) | Problem, motivation, scope and success criteria |
| [Development plan](docs/PLAN.md) | Phases, acceptance gates and implementation tasks |
| [Architecture](docs/ARCHITECTURE.md) | Intended pipeline and current skeleton boundaries |
| [Decision history](docs/DECISIONS.md) | Why the agreed choices were made |
| [Privacy and security](docs/PRIVACY.md) | Permissions, retention and release audit |
| [Device validation](docs/VALIDATION.md) | Pixel/Garmin acceptance and evidence checklist |
| [Development](docs/DEVELOPMENT.md) | Build, checks and contribution rules |
| [Task index](docs/TASKS.md) | Dart epics and implementation tasks |

Additional messaging apps and watch platforms are future work, after the Telegram/Garmin path is proven.

## License

[Apache License 2.0](LICENSE).
