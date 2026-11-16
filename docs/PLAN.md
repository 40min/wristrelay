# Release plan

WristRelay passed its first Garmin test on October 13, 2026. ONE, TWO and THREE reached the watch as separate, browsable entries. The Android relay now includes persistent settings, Telegram actions, optional original hiding and per-copy expiry. Spotify muting is a separate opt-in feature.

The original feasibility, foundation and relay epics are historical. The [Dart board](https://app.dartai.com/d/wNI0RZCjzhaw-WristRelay) tracks current work.

## Before a public release

- Record the supported Android, Telegram, Garmin Connect and watch firmware versions and confirm the setup instructions against them.
- Use the [device checklist](VALIDATION.md#checks-still-open) for newer options and compatibility limits. Keep older device results with their original dates and scope.
- Review the release APK's permissions, dependencies and data handling. The October 13 audit and measurements describe an earlier build.
- Settle release signing, artifact checksums and publication. Keep signing material outside the repository.

The [decision history](DECISIONS.md) records the product constraints that still apply.
