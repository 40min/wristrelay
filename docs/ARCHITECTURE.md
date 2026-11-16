# How WristRelay works

WristRelay has one Kotlin/Compose application module. Android binds `RelayNotificationListener` and delivers notification callbacks. Garmin Connect mirrors the resulting phone notifications to the watch. WristRelay has no direct watch connection.

## From Telegram snapshot to relay copy

1. The relay requires both the master and Telegram settings. The UI changes them together. The listener filters unrelated packages before relay extraction. Spotify and opt-in debug capture have their own gates.
2. `TelegramExtractor` reads the cumulative MessagingStyle array from `org.telegram.messenger`. It keeps the array order, takes the sender from each entry and skips entries without usable text. Unsupported snapshots and summaries produce no relay messages.
3. `TelegramRelaySession` manages tracking and original dismissal for each listener connection. It uses active notifications as a silent baseline when connecting or enabling relay. Messages already present at that point may be missed.
4. `RelayNotificationPublisher` posts a separate notification for each new message on a silent channel. A shared process counter and a check against active copies prevent reuse of active tag/ID pairs.

### How new messages are detected

Telegram's snapshots supply positions and timestamps. They expose no stable message ID. When an array grows, the tracker normally emits the appended entries in order, even if their text matches an earlier entry. When Telegram repeats or rebuilds an array, the tracker uses per-message timestamps to detect entries newer than the last relayed timestamp. A timestamp never changes the display order. Equal, missing or older timestamps can hide a genuinely new entry inside a rebuilt array. An unchanged restored array stays silent.

If the tracker has no cursor, it takes a multi-entry snapshot as a baseline and can relay a single-entry snapshot. This avoids replaying a backlog after cursor eviction at the cost of possibly skipping a fresh batch or repeating a lone entry.

The cursor advances through successfully submitted entries. After a partial publication, a later qualifying snapshot can retry the remainder. The tracker keeps up to 64 timestamps for retry alignment when Telegram drops entries from the front. Timestamp collisions and missing values can make the overlap ambiguous.

## What can remove a notification

With **Hide Telegram originals** enabled, WristRelay waits for its own posted callbacks and checks that all new copies are active and the original key and post time still match. Unsupported, baseline and duplicate-only snapshots cannot trigger dismissal. Failure to submit a copy suspends original hiding until reconnect. Confirmed removal by WristRelay or a repost with a distinct post time starts a fresh series. A repost with the same key and post time can be indistinguishable from a delayed old callback.

Telegram source removal never removes relay copies. With **Auto-clear relay notifications** enabled, Android receives a timeout at publication and handles expiry for each copy.

Copies carry valid Telegram-owned content and action tokens. On API 31+, the internal `RelayActionReceiver` wraps eligible non-activity actions. After a successful Android handoff, it cancels only the still-matching copy. An identity stored with the action guards against ID reuse. Activity tokens and actions on API 29/30 stay direct. See [ADR-015](DECISIONS.md#adr-015--dismiss-the-explicitly-acted-on-copy-after-handoff-2026-10-22) for the supported action cases.

## Settings and diagnostics

DataStore saves preferences only. A failed settings write leaves the last saved values visible. If the listener cannot read settings, it stops relay and Spotify muting until it reconnects and loads them.

Debug builds expose two off-by-default tools. General notification capture keeps up to **200** metadata events. The Spotify probe keeps up to **256** classified events and shows the latest five. See [Development](DEVELOPMENT.md) for controls and [Privacy](PRIVACY.md) for what they collect.
