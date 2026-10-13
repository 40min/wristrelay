# Device validation

Status: active device validation (run log below). Record device OS, Telegram package/version, Garmin Connect version, watch firmware, channel configuration and date for each run. Use synthetic messages; avoid private content in screenshots/logs; withhold device/uid-identifying numbers (model names, serials, notification-key id/uid fragments, battery capacity).

## Phase 0 Go/No-Go

1. Owner grants Android Notification Access and posting permission.
2. Owner enables WristRelay forwarding and disables Telegram forwarding in Garmin Connect, preserving Telegram phone notifications.
3. Send ONE, TWO, THREE to one conversation without opening Telegram.
4. Confirm extraction of all three from source snapshots.
5. Publish three independent WristRelay entries.
6. Confirm correct text and intended alerts, then browse all three on the Garmin watch.

Pass only if all three remain independently accessible. If Connect or the watch groups/suppresses entries, investigate notification IDs/tags/channels/grouping before declaring Go. The skeleton implements metadata-only debug capture (step 3–5 behavior is not implemented yet); its debug screen is the evidence source for step 4.

### Task 0.1 metadata inspection

1. Owner installs the debug APK and grants Notification Access to WristRelay.
2. Owner posts notifications externally (Telegram and, to see the listener working for sure, any other app such as a messaging app or a test tool).
3. Owner opens WristRelay's debug screen, taps Refresh and records the sanitized lines shown (they contain package/user/key/timing/extra-shape only — safe to share).
4. Repeat for: first message in a chat, second message in the same chat (update), third message, and after clearing the chat. Note POSTED vs REMOVED lines.

### Task 2.4 owner run script (feeds ADR-008 decision — completed 2026-10-13)

Run once during live daily use, relay toggles on, tokens ONE/TWO/THREE/OK only. Each answer is two facts: what survived, and yes/no REMOVED line in the debug screen. No message content.

- R1 — Source removal vs unread watch entries (ADR-008 core evidence): receive TWO and leave it unread; swipe away only Telegram's phone notification; record whether the WristRelay relay entry for TWO survives on the phone and on the watch, and whether a REMOVED line for the Telegram key appears in the debug screen.
- R2 — System clear-all: with THREE unread (Telegram entry + relay entry visible), press the system clear-all; record which entries vanished and whether the watch copies remain.
- R3 — Dismissal as explicit cleanup: with TWO unread on the watch, swipe away WristRelay's relay entry on the phone; record whether the watch copy disappears too.
- R4 — Restart/rebind: force-stop WristRelay (or reboot), then send OK; record (a) whether OK relays exactly once, (b) no replay of older entries, (c) the first RELAY line after reconnect (`extracted=N emitted=0`). Note: OK arriving while the listener is down is expected to be swallowed by the connect baseline (documented limitation, no replay storm by design).
- R5 — Access revoke/regrant: revoke Notification Access, record the status line and that nothing crashes; re-grant, send OK, expect exactly one relay entry and no replay.
- R6 — Posting revoke/regrant: revoke POST_NOTIFICATIONS (Android 13+), send OK (needs the synthetic-message helper from step 0.x), record that Telegram works but no relay entry appears and the app does not crash; re-grant, send OK again, relay resumes exactly once.

Results recorded in the Run log below; the outcome became ADR-012 (no automatic cleanup).

### Task 2.3 persistent settings (open device item)

Owner live check: after reinstall, toggles survive a process kill and app relaunch; the "Send test relay notification" button posts one silent-channel entry (watch mirrors it); the guide maps to what the owner's Connect version actually shows (record exact menu path per the 0.3 follow-up).

### Task 2.2 silent channel (open device item)

Phone: relay notifications are silent by design (new channel `wristrelay.relayed_messages_silent`, IMPORTANCE_LOW, no sound; the legacy loud prototype channel is untouched). Watch: **owner-confirmed 2026-10-13** — the test relay notification reached the Garmin watch with vibration through the silent channel; mirroring with the silent channel is verified. Any later watch change is processed as a regular issue (owner decision 2026-10-13).

## Parser variant coverage (task 2.1)

Fixture statements (unit-tested, synthetic vocabulary only) vs. device-unverified statements:

- Verified on device in 0.1/0.2 and now fixture-covered: cumulative in-place updates, identical consecutive texts, individual chat shapes, per-message senders in groups, array-order authority.
- Fixture-covered but never observed in a real Telegram snapshot (behavior defined by fixtures until device evidence exists): legacy plain-string `sender` payloads, `Int`-shaped per-message `time`, missing title/group flag, media-only or text-less entries interspersed with real messages, non-Bundle array elements, summary-style snapshots without `EXTRA_MESSAGES`, out-of-order per-message timestamps, array reset-shrinks (proven 0.2 for shrink handling in MessageTracker).
- Media-only messages are skipped: an image/sticker arrival produces no watch entry unless Telegram also includes a caption text. Improving this would require a device fixture; per owner decision (2026-10-13) the group/media shapes are not reproduced on demand — they are left to live daily use, and any discovered issue is reported as a separate bug.
- Whole-conversation summaries and unsupported shapes emit nothing and are not read evidence; the MessageTracker cursor is untouched (ADR-008).
- Telegram X (`org.telegram.messenger.x` or similar) remains unverified; only `org.telegram.messenger` is supported by the package gate.

## Reliability matrix

| Scenario | Required result |
|---|---|
| Cumulative snapshots ONE; ONE/TWO; ONE/TWO/THREE | Exactly three relay entries |
| Two distinct identical texts | Both delivered |
| Duplicate callback / existing snapshot | No new duplicate |
| Two chats / group chat | Correct conversation and sender |
| Long/media/redacted message | Safe meaningful presentation or documented limitation |
| Edit / source replacement / removal | No misleading new message or premature unread cleanup |
| Dismiss / explicit cleanup | Only appropriate relay entries removed |
| Process restart / listener reconnect | No stale notification storm |
| Access/posting revoked | Graceful stop and accurate status |
| Locked phone / burst / Bluetooth reconnect | Documented supported behavior without uncontrolled replay |
| Watch privacy / activities | Behavior matches configured settings |

## Performance evidence

Measure typical/burst processing and 24-hour idle/daily workload under stock battery restrictions. Targets are <20ms typical processing, <30MB normal memory, additional battery aim <1% per day, negligible idle CPU and zero app network traffic. Record actual results, measurement method and deviations; do not present targets as achievements.

## Run log

### 2026-10-13 — Task 0.1 Telegram metadata inspection

Date and versions: Android phone, Telegram `org.telegram.messenger` (exact OS/Telegram/Connect versions not yet recorded — see outstanding items).

Scenario and configuration: WristRelay debug capture installed, Notification Access granted; synthetic messages sent into one Telegram chat without opening Telegram; debug screen refreshed between sends.

Expected / observed:

- One phone notification per chat expected; three POSTED events captured for ONE/TWO/THREE, all with the identical key `0|org.telegram.messenger|<notification-id>|null|<uid>` (exact numeric id/uid withheld as identifying) and no intervening REMOVED — Telegram updates one notification in place and reuses the id.
- `android.messages: Parcelable[]` grows 1→2→3 (cumulative MessagingStyle); `android.text` len 3/3/5; `conversationUnreadMessageCount` len=1 while its value changes (1→2→3).
- Conversation metadata present and constant: `android.title` (len 12), `android.isGroupConversation`, `android.people.list`, `android.selfDisplayName`.
- REMOVED event for the same key carries a stripped extras bundle (conversationUnreadMessageCount/isGroupConversation/progress only) — removal metadata is not a reduced copy of the content.
- Identical-text test: two OKs in one chat produced two POSTED updates (`android.messages` len 3→4), each arrival one new array entry; the texts were merged into the single phone notification.
- Capture mechanism proven for an external app (`com.microsoft.skydrive` POSTED, full shape). Capture delay consistently ~280–310ms after postTime.

Sanitized evidence: full POSTED/REMOVED metadata records (package/user/key/timing/extra key-type-length summaries, no message text) recorded in the owner's report of 2026-10-13 and summarized in the Dart task comment.

Result: Pass

Go/No-Go and outstanding issues: not yet a Go/No-Go (that is task 0.3). Outstanding: (1) other Telegram package variants (e.g. Telegram X) unverified — only `org.telegram.messenger` installed; (2) device OS/Telegram/Garmin Connect versions not recorded; (3) per-message bundle internals (stable message identity keys) not yet inspected — the extractor must treat messages as unnamed, positionally identified entries and confirm which identity keys exist during 0.2 verification.

### 2026-10-13 — Task 0.2 relay prototype (phone-side)

Date and versions: Android phone, Telegram `org.telegram.messenger`, WristRelay task 0.2 prototype build (commits `407b88b`…`8847a06`); exact app versions not yet recorded.

Scenario and configuration: relay toggle on, notification access granted, synthetic ONE/TWO/THREE/OK messages into one Telegram chat without opening Telegram; metadata-only relay trace enabled.

Expected / observed:

- ONE/TWO/THREE each produced a separate, unchanged WristRelay notification; Telegram's own notification remained untouched. Owner confirmed texts correct.
- Trace evidence: `extracted=2 emitted=1 published=1` (baseline prevented replay of the leftover chat entry), then a snapshot with a **fresh one-entry messaging array** mid-session (`extracted=1 emitted=0`) treated correctly as shrink/read; growth after reset (`extracted=2 emitted=1 published=1`) delivered TWO.
- Identical texts (`OK`, `OK`) → two separate WristRelay entries, both delivered (owner-confirmed).
- One earlier anomaly (first prototype build): TWO was not relayed once; not reproduced on the trace build; recorded as unexplained, possibly listener rebinding at toggle time. Watch for repeats in 0.3/1.x validation.
- Phone channel is deliberately non-silent; owner's phone was in permanent silent mode, so channel sound behavior is inconclusive (recorded as an open item for 3.1).

Sanitized evidence: trace (`RELAY` lines with counts only) and POSTED metadata records in the owner's report of 2026-10-13; no message content in logs.

Result: Pass

Go/No-Go and outstanding issues: phone-side acceptance met (proves extraction + independent publication). No Go/No-Go yet — Garmin Connect mirroring and watch readability are task 0.3. Open items: unexplained one-off missed message; silent-channel mirroring unverified; package variants beyond `org.telegram.messenger` unverified.

### 2026-10-13 — Task 0.3 Garmin Connect filtering and Phase 0 Go/No-Go

Date and versions: Android phone + Garmin watch + Garmin Connect; exact OS/Connect/firmware versions not yet recorded (open follow-up).

Scenario and configuration: task 0.3 owner setup — in Garmin Connect the owner disabled Telegram app forwarding, enabled WristRelay, left other apps unchanged; Telegram phone notification settings untouched.

Expected / observed:

- Owner test: ONE, TWO, THREE each arrived on the Garmin watch as a separate, independent entry, each with vibration, correct synthetic text.
- After the six phone-side entries (Telegram's own + WristRelay's), the watch received exactly the WristRelay entries — no Telegram duplicates on the watch, consistent with the intended app-filtering split (owner: "doubled delivery on mobile from Telegram and WristRelay, as expected"; watch confirmation of no Telegram entries pending owner's explicit recheck).
- Telegram phone notifications preserved.

Sanitized evidence: owner's report of 2026-10-13 (quoted above, verified in session log); no private content.

Result: Pass

Go/No-Go and outstanding issues: **Go** — owner declared Phase 0 success: correct text, vibration, all three entries independently browsable. Production relay work (Epic 2, blocked via task 2.1) may start. Open items: record the exact Connect settings menu path and Connect version; explicit confirmation that Telegram itself produced no watch entries; suitable silence later against watch forwarding is a 2.2/3.1 concern.

### 2026-10-13 — Task 2.4 finding: first-of-series message swallowed (diagnosis + fix)

Date and versions: Android phone, `org.telegram.messenger`, WristRelay build with tasks 2.1–2.3 (`4345edb`).

Scenario: live sanity check (one/two/three sent into one chat; relay toggles on) plus controlled repro with the new debug traces (`d1e024d`): ONE alone — nothing relayed; series — first message missing, remainder relayed; repeated 3×, also with 4 messages.

Expected / observed:

- Telegram phone-side showed all entries cumulatively (`android.messages` len 1→2→3); WristRelay relayed only the 2nd+ messages. Trace pattern with zero `CONNECT`/`BASELINE` lines: `RELAY extracted=1 emitted=0` for the first, then emitted=1 — so no listener rebind; the tracker's positional cursor treated Telegram's rebuilt fresh array (smaller than the previous series) as "shrink = read", swallowing the genuinely new message inside it.
- Root cause: Telegram rebuilds the cumulative array from scratch at times (device-verified behavior); positional-only detection cannot distinguish rebuilt-new-content from unread-old-leftovers.

Fix (commit after `d1e024d`): `MessageTracker` cursors now also carry the newest relayed message timestamp. Shrink snapshots classify entries by per-message timestamp: strictly newer than the relaid watermark → emitted (rebuilt fresh arrays relay their content); older-or-equal → already-relayed leftovers, silent. Entries without timestamps keep conservative behavior. Battery for WristRelay: "Optimized" (stock).

Sanitized evidence: debug trace quoted by the owner 2026-10-13 (metadata/format only: POSTED len/counts, RELAY emitted counts, key `0|org.telegram.messenger|<notification-id>|null|<uid>`; exact numeric id/uid withheld as identifying); no message content.

Result: Pass — fix verified on device (owner re-test on build `2c20760`): a lone ONE relays again, and ONE/TWO/THREE all arrive as independent entries after the array rebuild.

Go/No-Go and outstanding issues: swallow resolved. Open limitation recorded: late-arriving offline messages whose per-message timestamp is older than the watermark are treated as already-relayed and stay silent. R1–R6 evidence script pending owner.

### 2026-10-13 — Task 3.1 watch presentation (partial, owner-decided scope)

Date and versions: Android phone + Garmin watch + Garmin Connect; relay build with all Epic 2 fixes installed and in daily use since 2026-10-13.

Scenario and configuration: relay toggles on; silent relay channel; Telegram forwarding off / WristRelay on in Garmin Connect (owner 0.3 setup unchanged).

Expected / observed:

- Long messages relayed as independent entries (owner live check, 2026-10-13).
- Phone locked: relay notifications still mirror to the watch (owner live check, 2026-10-13).
- Silent-channel mirroring with vibration confirmed earlier the same day (see 2.2 section).
- Notifications arrive once, and R-series evidence shows no duplicates through restart/rebind/regrant.

Sanitized evidence: owner's staged replies of 2026-10-13 (Stages 2–3, S1, R1–R6); no message content.

Result: Pass (owner-verified subset)

Go/No-Go and outstanding issues: owner decision 2026-10-13 — the remaining watch-side cases (rapid arrivals, Bluetooth reconnect, watch privacy/activity settings, exact Garmin Connect menu path and versions) are NOT reproduced on demand; anything discovered in daily use will be processed as a regular issue. Excellent 3.3 notes: record OS/Telegram/Connect versions for release docs when convenience allows.

### 2026-10-13 — Task 2.4 owner evidence run R1–R6

Date and versions: Android phone + Garmin watch + Garmin Connect; WristRelay with the timestamp-classification fix (`2c20760`+); battery stock "Optimized".

Scenario and configuration: relay toggles on; R1–R6 per the script above; synthetic vocabulary only.

Expected / observed:

- R1: owner removed only Telegram's phone notification → the relay entry survived on the phone and the watch. Telegram removal is therefore **not** read evidence and must never trigger cleanup.
- R2: system clear-all removed Telegram and relay entries on the phone; watch copies disappeared as well (mirroring follows user-initiated phone cancellations).
- R3: dismissing WristRelay's relay entry on the phone removed the watch copy (explicit user cleanup; expected mirror behavior).
- R4: force-stop then OK → relayed exactly once, no stale replay (the reconnect baseline behaved as designed).
- R5: Notification Access revoke showed the status line without crashing; re-grant → OK relayed once, no duplicates.
- R6: posting revocation was exercised by the owner previously (graceful suppression, no crash) and is unit-covered by the posting-disabled publisher test.

Sanitized evidence: owner's one-line answers per the script (single facts, no message content), 2026-10-13.

Result: Pass

Go/No-Go and outstanding issues: ADR-012 recorded (no automatic cleanup — see DECISIONS.md). Phone-side daily-use behaviors are validated; the remaining reliability-matrix rows are watch-side and belong to task 3.1 (locked phone, Bluetooth reconnect, watch privacy settings, long/silent mirroring).

### Template for future runs

Date and versions:

Scenario and configuration:

Expected / observed:

Sanitized evidence:

Result: Pass / Fail / Unverified

Go/No-Go and outstanding issues:
