# Development plan

Status: planning; Android skeleton only. Production Telegram relay remains unimplemented.

## Acceptance gates

- **v0.1 — Proof of concept:** Pixel 8 + Garmin vívoactive 5 display ONE, TWO, THREE as three independently browsable notifications.
- **v0.2 — Private beta:** daily-use reliability, settings and security hardening accepted.
- **v1.0 — Public release:** audits, profiling, release process and documentation complete.

Phase 0 is the critical gate. Foundation can proceed alongside it; production parser/relay expansion waits for its Go decision. No per-conversation replacement strategy is planned.

## Phase 0 — Feasibility prototype

Prove on Pixel 8 and Garmin vívoactive 5 that ONE, TWO, THREE produce three correct, independently browsable watch notifications. This is the gate before production relay work.

### 0.1 — Complete the minimal permission and debug screen

Extend the initial skeleton with privacy-safe metadata-only debug facilities and notification access/posting controls. Build an installable prototype. No message bodies in persistent logs.

### 0.2 — Inspect Telegram notification structures

On device inspect MessagingStyle, extras types, conversation identifiers, timestamps, notification keys and update/removal behavior for ONE/TWO/THREE. Record sanitized structures without real message contents. Verify supported package variants.

### 0.3 — Prove extraction of three distinct messages

Use captured synthetic fixtures to extract ONE, TWO, THREE from successive snapshots. Preserve two legitimate identical-text messages; document missing identity metadata and limitations.

### 0.4 — Publish three independent prototype notifications

Generate unique IDs/tags and readable ungrouped ONE/TWO/THREE notifications. Do not overwrite earlier entries or touch source Telegram notifications.

### 0.5 — Configure Garmin Connect application filtering

Owner device setup: disable Telegram forwarding to watch, enable WristRelay, preserve Telegram phone behavior and other app settings. Record verified setup path.

### 0.6 — Verify Garmin feasibility and record Go/No-Go

Owner device test: correct text, vibration and all three entries independently browsable. Investigate IDs/tags/channels/grouping if failure. Record evidence and explicit Go/No-Go; no production relay expansion before Go.

## Phase 1 — Project foundation

Establish a maintainable single-module Kotlin/Compose application with minimal permissions, manual wiring and build checks.

### 1.1 — Harden foundation and source permission gates

Review initial Gradle/manifest/structure; complete notification access and posting handling, disabled-by-default source gates, immediate unrelated-package filtering and self-notification exclusion. No internet/backup/content logs.

### 1.2 — Complete CI, static analysis and test infrastructure

Review scaffold GitHub Actions and Gradle checks. Add Detekt and AndroidX/Robolectric as needed, synthetic fixture conventions and merged-manifest checks. Ensure clean-checkout build and lint/unit tests run in CI.

### 1.3 — Maintain project and developer documentation

Reconcile README, context, architecture, decisions, build/setup and privacy docs with implemented behavior. Keep physical acceptance explicitly separate from automated checks.

## Phase 2 — Telegram parser

Extract normalized messages reliably from supported Telegram notification formats with synthetic fixtures.

### 2.1 — Implement Telegram source recognition and normalized parsing

Implement NotificationSource and RelayMessage based on actual Phase0 metadata. Support verified variants, individual/group conversations, title/sender/body/time/identity and ordering; skip summaries and handle missing/redacted/media metadata safely.

### 2.2 — Cover parser variants, updates and identical texts

Fixture tests for chats/groups, summaries, cumulative snapshots, edits, identical consecutive messages, out-of-order timestamps and unsupported structures. Document supported formats without claiming unavailable unique IDs.

## Phase 3 — Message detection and relay

Create exactly one independent notification per genuinely new message, retaining legitimate repeated texts.

### 3.1 — Implement snapshot tracking and bounded deduplication

Compare notification snapshots with available identities and state. ONE then ONE/TWO then ONE/TWO/THREE emits exactly three. Distinct identical texts remain distinct. Define bounded in-memory retention and limitations.

### 3.2 — Implement independent notification publication and IDs

Unique active IDs/tags, silent phone channel, small-screen chat/group presentation. No update/overwrite/group collapse, no recursive processing, no source notification mutation. Handle multiple conversations and bursts.

### 3.3 — Verify relay event sequences and resource bounds

Unit/integration tests for repeated callbacks, cumulative snapshots, multi-chat bursts, edits, identical text, ID collision avoidance, bounded cache eviction and permission failure.

## Phase 4 — Garmin integration and settings

Validate presentation and watch behavior and provide minimal daily-use controls.

### 4.1 — Implement persistent settings and setup guide

DataStore for settings only: master relay switch, explicit Telegram switch, access/posting status, test notification and Garmin app-filtering guide. No message history or telemetry.

### 4.2 — Validate channel, vibration, text and browsing on Garmin

Owner test Pixel8/vívoactive5: independent entries, sender/group info, long messages, rapid arrivals, phone locked, watch privacy/activity settings. Confirm silent phone channel still forwards correctly.

### 4.3 — Validate Bluetooth reconnect and application filtering

Owner test connected/disconnected/reconnected behavior, no uncontrolled replay, only WristRelay forwarded while Telegram phone notifications remain intact. Record evidence and supported limits.

## Phase 5 — Lifecycle and reliability

Handle everyday lifecycle changes without stale storms or premature loss of unread messages.

### 5.1 — Define and implement conservative notification cleanup

Validate Telegram removal/replacement/read behavior before automatic cleanup. Map source state to relay entries, support explicit cleanup and user dismissals, never cancel Telegram. Protect unread watch entries and bound retained state.

### 5.2 — Handle listener reconnect, restart and permission revocation

Choose baseline/recovery behavior from actual metadata. No stale replay storm after process/service restart; graceful access/posting revocation and regrant, disabled sources and missing/redacted content.

### 5.3 — Run daily-use reliability acceptance matrix

Automated plus owner checks: three/identical messages, updates, two chats, long/media/edited messages, dismiss/read/replacement, locked phone, bursts, restarts and disconnects. Document limitations and private-beta readiness.

## Phase 6 — Security and performance audit

Verify offline data handling, component protection, bounded work and measured performance.

### 6.1 — Audit permissions, components, dependencies and data flow

Inspect merged manifest, no INTERNET/unnecessary permissions, protected listener, exported entrypoints, dependencies/vulnerabilities, logs, temporary content retention, recursion, permission revocation and release config. No analytics or message persistence.

### 6.2 — Profile latency, memory, idle and battery behavior

Measure typical/burst workload, 24h idle, locked phone, Bluetooth changes and stock battery restrictions. Targets: typical processing <20ms, memory <30MB, additional daily battery aim <1%, zero network. Report measured results; no exemption unless justified.

## Phase 7 — Packaging and public release

Prepare a maintainable public release after feasibility, reliability and audit gates pass.

### 7.1 — Prepare controlled release builds and checksums

Document signing ownership without committing keys; reproducible clean-checkout build steps, release CI, APK artifacts/checksums and dependency review. No automatic device installs or key provisioning.

### 7.2 — Finalize user docs, support limits and release acceptance

Publish setup/privacy/troubleshooting, supported Telegram/device versions, known limitations, license and release checklist. Milestones v0.1 PoC, v0.2 private beta, v1.0 public. Gate release on evidence and unresolved audit findings.

## Plan provenance

This plan transcribes the latest agreed notification behavior and phases 0–4 from the planning conversation. The chat reader truncated its final message during Phase 5. Phases 5–7 above consolidate the readable security/performance requirements and earlier roadmap, rather than claim verbatim recovery of the unavailable tail. Future sources and watch platforms are deferred beyond v1.0. Initial estimates are not delivery commitments.
