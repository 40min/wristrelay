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

## Audit results (task 3.2)

Static audit recorded 2026-10-13 (commits through `1aedc60`):

- Manifest: exactly two permissions — `POST_NOTIFICATIONS` only; no INTERNET, no accessibility/contacts/SMS/location/wake-lock/battery-exemption. Single module `dev.wristrelay.app`. MainActivity is the public launcher entry point. The listener service is exported with the system-binding guard `BIND_NOTIFICATION_LISTENER_SERVICE` (the minimal correct NotificationListenerService shape: only the system can bind it; nothing else is exported).
- Backups: `allowBackup=false`, `fullBackupContent=false`; `data_extraction_rules.xml` excludes every domain for both cloud backup and device transfer — no app data (not even settings) leaves the device.
- Data flow: Telegram snapshot extras are read in memory only; the only persistence is DataStore settings (two booleans). No SQLite/files/external storage/code paths exist (grep-audited). Debug capture is metadata-only (type + length), bounded to 200 events, process-lifetime, never logged or persisted.
- Logging: zero `Log.*`/`println` in main sources (grep-audited). The only diagnostic output is the on-device debug screen.
- Recursion/self-processing: the listener's first gate is the app's own package; the relay pipeline never sees WristRelay notifications and never cancels anything (ADR-012).
- Third-party dependencies: androidx activity-compose/compose ui+foundation+material3, androidx datastore-preferences 1.2.1. JUnit/Robolectric/androidx-test for tests only (not in the APK). No network, analytics, crash-reporting or telemetry libraries.
- Build integrity: Gradle wrapper pinned (Gradle 9.4.1, AGP 9.2.0, Kotlin 2.2.10 built-in toolchain), CI runs `assembleDebug lintDebug testDebugUnitTest detekt` on every push, checks pass no-source-run semantics (test success implies coverage questions are answered separately, not assumed).
- Release signing: absent by design; debug-signed builds only. Release material is task 3.3 work and stays outside the repository.

Still open for 3.2 (needs a device session, deferred to live use or a later session): measured typical/burst processing time (trace instrumentation `proc=Xms` shipped 2026-10-13 so live use accumulates the evidence), memory under normal operation, daily battery delta, idle CPU, and the 24-hour idle workload profile.

## Dynamic measurements (2026-10-13, read-only adb, owner device during normal use)

Measurement method: `dumpsys` reads over USB for the relay process/uid only; owner-side burst test (14 synthetic messages sent back-to-back); statistics window = the last full charge (4h 30m of heavy interactive use); battery "Optimized" (stock), no exemptions.

- Processing time (typical + burst): every relay event in the burst — 14/14 relayed — shows `proc=5ms`/`proc=7ms` per event in the RELAY trace. Target <20ms met with 3× headroom. (Note: ~300ms capture delay is the system callback latency, out of scope for processing cost.)
- CPU / idle: battery-stats record 830ms user + 1.65s kernel CPU for the whole 4.5h window (~2.5s total); instant `cpuinfo` snapshot 0.5% (one core's worth of context, mostly kernel). Event-driven by construction, zero polling.
- Battery: UID `u0a470` estimated drain 1.80 mAh over the 4.5h-window against 4374 mAh capacity ≈ 0.04% of a charge; a uniform-24h extrapolation stays ≈0.1%/day — an order of magnitude under the <1%/day target. No self-acquired wake locks (the batterystats wake-lock rows attributed to the uid are system-side attribution such as the post-notification hold, total ~1–3s in window).
- Network: zero — by construction (no INTERNET permission, no network deps) and empirically (the uid appears nowhere in battery-stats network accounting rows; cells list other uids).
- Memory: steady-state listener-only measurement (2026-10-13, launcher screen swiped away, process holds only the listener service): private PSS 31.4MB (Java 5.8 / Native 5.3 / Code 13.7 / Stack 0.3 / Graphics 0 / Private Other 6.4), shared-system attribution 18.9MB, TOTAL PSS 50.3MB. The literal "<30MB" target is not met; per ADR-013 the practical re-scoped memory evidence is exactly this measured floor of the Compose/Kotlin stack, with the relay's own processing state negligible (a bounded map of conversation keys to counts/timestamps). An earlier capture with the UI paused-alive measured 36.2MB private / 62.9MB total — kept for the record.
