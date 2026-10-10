# Device validation

Status: not performed. Record device OS, Telegram package/version, Garmin Connect version, watch firmware, channel configuration and date for each run. Use synthetic messages; avoid private content in screenshots/logs.

## Phase 0 Go/No-Go

1. Owner grants Android Notification Access and posting permission.
2. Owner enables WristRelay forwarding and disables Telegram forwarding in Garmin Connect, preserving Telegram phone notifications.
3. Send ONE, TWO, THREE to one conversation without opening Telegram.
4. Confirm extraction of all three from source snapshots.
5. Publish three independent WristRelay entries.
6. Confirm correct text and intended alerts, then browse all three on vívoactive 5.

Pass only if all three remain independently accessible. If Connect or the watch groups/suppresses entries, investigate notification IDs/tags/channels/grouping before declaring Go. The skeleton does not implement steps 3–5 yet.

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

## Run record template

Date and versions:

Scenario and configuration:

Expected / observed:

Sanitized evidence:

Result: Pass / Fail / Unverified

Go/No-Go and outstanding issues:
