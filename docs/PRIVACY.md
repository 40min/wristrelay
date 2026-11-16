# Privacy and permissions

WristRelay processes notifications on the phone. It has no internet permission, telemetry or persistent message history. DataStore saves settings only. Android backup and device transfer are disabled for app data. Android and Garmin Connect handle their own copies of the notifications.

## What Android grants

- **Notification Access** lets the Android system deliver notifications from other apps to the listener. This is a broad grant. WristRelay filters incoming events before reading message content for enabled features. The exported listener requires the system binding permission `BIND_NOTIFICATION_LISTENER_SERVICE`.
- **Notification posting** lets WristRelay publish copies. Android 13 and later require the user to grant `POST_NOTIFICATIONS`.
- **Audio settings** lets the optional Spotify feature change shared media volume through `MODIFY_AUDIO_SETTINGS`.
- The launcher activity is public. The relay action receiver is internal to the app.

## What WristRelay keeps

The Telegram tracker holds bounded snapshot positions and timestamps in memory. Optional original hiding tracks source and copy identifiers until publication is confirmed. Telegram retains ownership of action tokens. Android holds the wrapper token and routing metadata, and WristRelay forwards submitted reply results to Telegram without storing or logging their content. Original delete intents are excluded.

Spotify ad mute reads notification text from Spotify while enabled. The previous volume and restore timer live in memory. WristRelay has no persistent record of Spotify notification text.

Release builds omit diagnostic capture. In debug builds, the general recorder keeps up to 200 events after explicit opt-in. That can include package names, notification keys, timing and extra types or lengths. The Spotify probe keeps up to 256 events with classifications of notification and media-session fields. Both tools keep their buffers in memory. The debug panel can copy them to the clipboard on request.

## Release review

Check the merged manifest, exported components, dependency vulnerabilities, data flow, logging and permission revoke/regrant behavior on the release build. Keep the [historical audit and device measurements](VALIDATION.md#october-2026-audit-and-measurements) for comparison.
