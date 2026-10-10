# Project context

WristRelay exists because updating a grouped Telegram notification may alert a Garmin watch while repeatedly presenting the first unread message. This is the reported motivating behavior, not a verified finding across all Telegram or Garmin versions.

The initial environment is Pixel 8, Telegram, Garmin Connect and Garmin vívoactive 5. Android NotificationListenerService exposes notification snapshots; WristRelay will interpret newly arriving messages and publish independent notifications for Connect to mirror. There is no direct Bluetooth or Garmin SDK integration.

## Product contract

One genuinely new message produces one independent notification. Three messages must remain browsable as three entries. Do not replace an existing message notification when a later message arrives. Do not deduplicate by text alone.

Telegram notifications on the phone remain untouched. The owner configures Garmin Connect to forward WristRelay and exclude Telegram. Other apps retain their existing forwarding settings.

## Scope

The MVP covers verified Telegram packages and formats, normalized chat/group messages, stateful detection, bounded in-memory deduplication, unique notification identities, conservative cleanup and minimal settings. Future source adapters are possible, but unsupported sources and watch platforms are beyond v1.0.

No cloud, analytics, root, accessibility access, database or persistent message history is planned. Settings may persist through DataStore; message contents may not.

## Uncertainties to resolve

- Telegram metadata may lack stable per-message identity.
- Summary, cumulative, replaced and edited notifications need actual fixtures.
- Source removal is not reliable evidence that all messages were read.
- Android may redact sensitive contents; protections must not be bypassed.
- Silent phone channels, notification grouping, watch retention, vibration and reconnect behavior require physical testing.
- Android/Telegram/Garmin versions and configuration may affect results.

The first Go/No-Go depends on the watch retaining ONE, TWO, THREE independently. Software checks cannot establish that hardware behavior.
