# Architecture

## Current implementation

One `app` Gradle module with Kotlin and Compose. A launcher screen provides notification permission controls plus a Phase 0 debug section (Refresh/Clear) that shows sanitized notification metadata captured by the listener. The manifest registers a notification listener guarded by Android's binding permission.

Phase 0 debug capture only: `RelayNotificationListener` records external posted/removed events (ignoring WristRelay itself) into a bounded in-memory store (`DebugEventStore`, max 50 entries, process-lifetime only). Each record is metadata-only: package, user, key, timing, ongoing flag and extra summaries that reduce string/char-sequence values to type and length. Message bodies, titles and sender names are never read into retained state; the store is not persisted or logged. The listener performs no extraction or relay yet.

## Intended pipeline

1. Listener receives Android posted/removed callbacks.
2. Source gate rejects WristRelay itself and all applications not explicitly enabled.
3. Telegram adapter extracts normalized messages and relevant snapshot metadata, ignoring inappropriate summaries.
4. Detector compares successive source states and identifies genuinely new messages.
5. Relay allocates unique active notification identities and publishes an independent entry per message.
6. Cleanup applies a policy validated against real removal/replacement behavior.

Responsibilities can live in `listener`, `source/telegram`, `model`, `processing`, `relay`, `settings` and `ui` packages. Create implementations when needed rather than empty placeholder classes. Source/parser and detector APIs remain provisional until Phase 0 provides actual metadata.

## State and identity

Normalized messages need source package, conversation identity/title, sender, body, timestamp and optional source identity. Android does not guarantee Telegram message IDs. Snapshot order and multiplicity may matter when identities are absent; sender/body alone cannot distinguish legitimate repeated texts.

Caches must be temporary and bounded. Settings are the only planned persistent data. Restarts/reconnections need a deliberate baseline policy to prevent stale replay; no message database is proposed.

Active relay notifications need unique IDs/tags that are not reused while active. New arrivals do not update previous entries. Channel/presentation choices are subject to Garmin acceptance. Automatic cleanup must not erase unread watch entries merely because Telegram replaced a source notification.

## Execution and dependencies

Use Android-managed listener callbacks, no polling or permanent foreground service. Filter early and keep callbacks short; move expensive work off the main thread if profiling requires it. Manual wiring is sufficient. Compose provides UI, DataStore will persist settings, JUnit and Android integration tooling verify behavior. No networking dependency is needed.
