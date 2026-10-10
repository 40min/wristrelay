# Privacy and security requirements

## Permissions

Notification Access is a broad Android special access grant, not a runtime permission scoped to Telegram. POST_NOTIFICATIONS is required on Android 13+ to publish relay notifications. The listener service is exported for Android system binding and protected by BIND_NOTIFICATION_LISTENER_SERVICE. The launcher activity is the public entry point.

No INTERNET, accessibility, contacts, SMS, location, root, wake-lock or battery-exemption requirement is planned. Backups are disabled in the skeleton.

## Data handling

- Filter disabled and unrelated source apps before reading their contents.
- Never persist message bodies, message history or real notification fixtures.
- Never log bodies or sensitive extras. Diagnostics must be off by default and metadata-only.
- Use synthetic fixture content and redacted evidence.
- Bound in-memory deduplication state and retain content only as long as required.
- Persist settings only; no analytics, ads or crash-reporting SDKs.
- Respect redaction by Android; do not bypass sensitive-notification protections.

## Release audit

Inspect the merged manifest and exported components, direct/transitive dependencies and vulnerability reports, content data flow/retention, logs, recursion prevention, permission revoke/regrant behavior and release configuration. Confirm zero application network permission and no accidental content persistence. Keep signing keys outside the repository.

These are requirements, not a completed security audit. Android and Garmin Connect retain their own notification data under their policies.
