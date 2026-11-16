# WristRelay

When Telegram adds a message to an existing Android notification, a Garmin watch can buzz again and still show the first unread message. WristRelay gives each new message its own phone notification so Garmin Connect can mirror it separately. Send ONE, TWO and THREE, and you should be able to read all three on the watch.

## Set it up

1. Open WristRelay on Android 10 or later. Grant **Notification Access** and, on Android 13 or later, allow WristRelay to show notifications.
2. Turn on **Show Telegram messages on the watch** under **Relay to watch**. WristRelay starts with new arrivals and skips notifications already on the phone.
3. In Garmin Connect, enable forwarding for WristRelay and disable it for Telegram. Leave Telegram's own Android notifications enabled. The **Garmin Connect guide** in the app walks through this setup.

WristRelay copies are silent on the phone. The October 2026 device run confirmed that Garmin could still mirror them with vibration. See the [device results](docs/VALIDATION.md) for the tested setup and open cases.

## Telegram options

- **Hide Telegram originals** dismisses a supported Telegram notification once Android shows all its new WristRelay copies. It starts off. It can leave the original visible if the copies can't be confirmed, and Telegram can still alert before dismissal.
- **Auto-clear relay notifications** gives each new copy its own expiry. It starts off. Set a duration under **Relay cleanup**, from 1 minute to 7 days (initially 60 minutes). Copies already posted keep their original expiry settings. Android controls when they disappear, and a watch mirror may disappear too.
- Copies can open the chat and carry actions Telegram supplies. On Android 12 and later, supported **Mark as read** and submitted inline **Reply** actions remove just that copy after Android accepts the handoff. Telegram controls action availability and delivery. On Android 10 and 11 the original actions run directly and leave the copy visible.

Swiping away a Telegram original leaves its WristRelay copies in place. Media-only messages without text are skipped. If Android or Telegram withholds content, WristRelay can only work with the snapshot it receives.

## Spotify ad mute

Under **Spotify**, you can opt in to muting shared Android media volume when Spotify's notification text contains “Advertisement”. This also silences other apps playing through the same media output.

WristRelay attempts to restore the old volume when the ad ends, the tracked notification disappears, or the timeout expires. The default is 30 seconds, adjustable from 1 to 300 seconds. If you raise the volume yourself, WristRelay leaves it there. If Android kills the process during a mute, use the phone's volume buttons.

## Build and contribute

Use JDK 17+ and Android SDK platform 37.0. Set `ANDROID_HOME` or configure your SDK in Android Studio.

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest detekt
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

- [Privacy and permissions](docs/PRIVACY.md)
- [Development and debug tools](docs/DEVELOPMENT.md)
- [How the relay works](docs/ARCHITECTURE.md)
- [Device results and remaining checks](docs/VALIDATION.md)
- [Release plan](docs/PLAN.md) and [decision history](docs/DECISIONS.md)

WristRelay processes notifications locally and saves only settings. It has no internet permission or message history.

[Apache License 2.0](LICENSE)
