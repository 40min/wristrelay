# Device validation

Status: not performed. Record device OS, Telegram package/version, Garmin Connect version, watch firmware, channel configuration and date for each run. Use synthetic messages; avoid private content in screenshots/logs.

## Phase 0 Go/No-Go

1. Owner grants Android Notification Access and posting permission.
2. Owner enables WristRelay forwarding and disables Telegram forwarding in Garmin Connect, preserving Telegram phone notifications.
3. Send ONE, TWO, THREE to one conversation without opening Telegram.
4. Confirm extraction of all three from source snapshots.
5. Publish three independent WristRelay entries.
6. Confirm correct text and intended alerts, then browse all three on vívoactive 5.

Pass only if all three remain independently accessible. If Connect or the watch groups/suppresses entries, investigate notification IDs/tags/channels/grouping before declaring Go. The skeleton implements metadata-only debug capture (step 3–5 behavior is not implemented yet); its debug screen is the evidence source for step 4.

### Task 0.1 metadata inspection

1. Owner installs the debug APK and grants Notification Access to WristRelay.
2. Owner posts notifications externally (Telegram and, to see the listener working for sure, any other app such as a messaging app or a test tool).
3. Owner opens WristRelay's debug screen, taps Refresh and records the sanitized lines shown (they contain package/user/key/timing/extra-shape only — safe to share).
4. Repeat for: first message in a chat, second message in the same chat (update), third message, and after clearing the chat. Note POSTED vs REMOVED lines.

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

Date and versions: Pixel 8, Telegram `org.telegram.messenger` (exact OS/Telegram/Connect versions not yet recorded — see outstanding items).

Scenario and configuration: WristRelay debug capture installed, Notification Access granted; synthetic messages sent into one Telegram chat without opening Telegram; debug screen refreshed between sends.

Expected / observed:

- One phone notification per chat expected; three POSTED events captured for ONE/TWO/THREE, all with the identical key `0|org.telegram.messenger|386558704|null|10278` and no intervening REMOVED — Telegram updates one notification in place and reuses the id.
- `android.messages: Parcelable[]` grows 1→2→3 (cumulative MessagingStyle); `android.text` len 3/3/5; `conversationUnreadMessageCount` len=1 while its value changes (1→2→3).
- Conversation metadata present and constant: `android.title` (len 12), `android.isGroupConversation`, `android.people.list`, `android.selfDisplayName`.
- REMOVED event for the same key carries a stripped extras bundle (conversationUnreadMessageCount/isGroupConversation/progress only) — removal metadata is not a reduced copy of the content.
- Identical-text test: two OKs in one chat produced two POSTED updates (`android.messages` len 3→4), each arrival one new array entry; the texts were merged into the single phone notification.
- Capture mechanism proven for an external app (`com.microsoft.skydrive` POSTED, full shape). Capture delay consistently ~280–310ms after postTime.

Sanitized evidence: full POSTED/REMOVED metadata records (package/user/key/timing/extra key-type-length summaries, no message text) recorded in the owner's report of 2026-10-13 and summarized in the Dart task comment.

Result: Pass

Go/No-Go and outstanding issues: not yet a Go/No-Go (that is task 0.3). Outstanding: (1) other Telegram package variants (e.g. Telegram X) unverified — only `org.telegram.messenger` installed; (2) device OS/Telegram/Garmin Connect versions not recorded; (3) per-message bundle internals (stable message identity keys) not yet inspected — the extractor must treat messages as unnamed, positionally identified entries and confirm which identity keys exist during 0.2 verification.

### 2026-10-13 — Task 0.2 relay prototype (phone-side)

Date and versions: Pixel 8, Telegram `org.telegram.messenger`, WristRelay task 0.2 prototype build (commits `407b88b`…`8847a06`); exact app versions not yet recorded.

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

### Template for future runs

Date and versions:

Scenario and configuration:

Expected / observed:

Sanitized evidence:

Result: Pass / Fail / Unverified

Go/No-Go and outstanding issues:
