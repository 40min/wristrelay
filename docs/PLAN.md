# Development plan

Status: planning; Android skeleton only. Phone-side relay is implemented; optional original hiding/actions require device acceptance. Task tracking lives on the [Dart board](https://app.dartai.com/d/wNI0RZCjzhaw-WristRelay), indexed in [TASKS.md](TASKS.md).

## Acceptance gates

- **v0.1 — Proof of concept:** an Android phone + Garmin watch display ONE, TWO, THREE as three independently browsable notifications.
- **v0.2 — Private beta:** daily-use reliability, settings and security hardening accepted.
- **v1.0 — Public release:** audits, profiling, release process and documentation complete.

Phase 0 (Epic 0) is the critical gate. Foundation can proceed alongside it; production parser/relay expansion waits for its Go decision. No per-conversation replacement strategy is planned.

## Epic 0 — Feasibility prototype

Prove on an Android phone and a Garmin watch that ONE, TWO, THREE produce three correct, independently browsable watch notifications.

- **0.1 — Permission and debug screen, Telegram metadata inspection.** Privacy-safe, metadata-only debug facilities and notification access/posting controls; build an installable prototype. On device, inspect Telegram MessagingStyle, extras types, conversation identifiers, timestamps, notification keys and update/removal behavior for ONE/TWO/THREE. Record sanitized structures without real message contents; verify supported package variants.
- **0.2 — Prove extraction and publish three independent notifications.** Extract ONE, TWO, THREE from successive snapshots using synthetic fixtures; preserve two legitimate identical-text messages and document missing identity metadata. Generate unique IDs/tags and readable ungrouped notifications; never overwrite earlier entries or touch source Telegram notifications.
- **0.3 — Configure Garmin Connect filtering and record Go/No-Go.** Owner device setup: disable Telegram forwarding, enable WristRelay, preserve Telegram phone behavior; record the verified setup path. Owner test: correct text, vibration and all three entries independently browsable; investigate IDs/tags/channels/grouping on failure. Record evidence and an explicit Go/No-Go — no production relay expansion before Go.

## Epic 1 — Project foundation

Maintainable single-module Kotlin/Compose application with minimal permissions, manual wiring and build checks. Runs alongside Epic 0.

- **1.1 — Harden foundation and source permission gates.** Complete notification access and posting handling, disabled-by-default source gates, immediate unrelated-package filtering and self-notification exclusion. No internet/backup/content logs.
- **1.2 — Complete CI, static analysis and test infrastructure.** Review scaffold GitHub Actions and Gradle checks; add Detekt and AndroidX/Robolectric as needed, synthetic fixture conventions and merged-manifest checks. Clean-checkout build with lint/unit tests in CI. A successful no-source unit-test task does not mean coverage exists.

## Epic 2 — Telegram relay and daily-use reliability

Exactly one independent notification per genuinely new message, retaining legitimate repeated texts; keep that behavior reliable in daily use.

- **2.1 — Implement Telegram parser with variant fixture coverage.** NotificationSource and RelayMessage based on actual Phase 0 metadata: verified variants, individual/group conversations, title/sender/body/time/identity and ordering; skip summaries; handle missing/redacted/media metadata safely. Fixture tests for chats/groups, summaries, cumulative snapshots, edits, identical consecutive messages, out-of-order timestamps and unsupported structures.
- **2.2 — Implement detection, independent publication and ID management.** Bounded snapshot deduplication: cumulative snapshots emit exactly three; distinct identical texts remain distinct. Unique active IDs/tags, silent phone channel, small-screen presentation; no update/overwrite/group collapse, no recursive processing, no source mutation except opt-in ADR-014 dismissal; handle multiple conversations and bursts. Unit tests for repeated callbacks, edits, ID collisions, bounded cache eviction and permission failure.
- **2.3 — Implement persistent settings and setup guide.** DataStore for settings only: master relay switch, explicit Telegram switch, access/posting status, test notification and Garmin app-filtering guide. No message history or telemetry.
- **2.4 — Implement cleanup, reconnect handling and reliability matrix.** Validate Telegram removal/replacement/read behavior before automatic cleanup; preserve Telegram by default (optional scoped dismissal in ADR-014); protect unread watch entries; bound retained state. No stale replay storm after restart; graceful access/posting revocation and regrant. Run the daily-use acceptance matrix (see [VALIDATION.md](VALIDATION.md)); document limitations and private-beta readiness.

## Epic 3 — Garmin validation, audit and release

- **3.1 — Validate watch presentation, reconnect and app filtering.** Owner test: independent entries, sender/group info, long messages, rapid arrivals, phone locked, watch privacy/activity settings; silent phone channel still forwards. Connected/disconnected/reconnected behavior without uncontrolled replay; only WristRelay forwarded while Telegram phone notifications remain intact. Record evidence and supported limits.
- **3.2 — Audit security and performance.** Merged manifest, exported components, no INTERNET or unnecessary permissions, protected listener, dependencies/vulnerabilities, logs, temporary content retention, recursion, revoke/regrant, release config; no analytics or message persistence. Profile typical/burst workload, 24h idle, locked phone, Bluetooth changes and stock battery restrictions. Targets: typical processing <20ms, memory <30MB, additional daily battery aim <1%, zero network. Report measured results; no exemption unless justified.
- **3.3 — Prepare controlled release and finalize user docs.** Document signing ownership without committing keys; reproducible clean-checkout builds, release CI, APK artifacts/checksums, dependency review. Reconcile all project docs with implemented behavior; publish setup/privacy/troubleshooting, supported versions, limitations, license and release checklist. Gate release on evidence and unresolved audit findings.

## Plan provenance

The original planning conversation was transcribed on 2026-10-10; its final message was truncated, so lifecycle/audit/release requirements were consolidated from readable sources rather than verbatim recovery. The board was simplified to 4 epics on 2026-10-12 with all acceptance criteria merged into task descriptions (originals in Dart trash). Future sources and watch platforms are deferred beyond v1.0. Initial estimates are not delivery commitments.
