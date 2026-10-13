# Decision history

Recorded 2026-10-10 from the agreed planning discussion. These decisions describe the intended product; implementation status is tracked separately.

| ID | Decision | Reason and consequence |
|---|---|---|
| ADR-001 | Use WristRelay as the project name | Describes a bridge to the wrist without binding the name to one source. No legal uniqueness claim is made. |
| ADR-002 | One message = one independent notification | The user needs to browse all messages on the watch. Per-conversation updates/replacements would lose that behavior and are rejected. |
| ADR-003 | Preserve Telegram phone notifications | Garmin Connect app filtering avoids duplicate watch forwarding without interfering with Telegram on Android. |
| ADR-004 | Never deduplicate by text alone | Two identical texts can be two legitimate messages. Detection must consider identity and snapshot state. |
| ADR-005 | Offline, no message persistence or telemetry | Notification access is sensitive. Minimize data handling, permissions and retention. |
| ADR-006 | Single Kotlin/Compose module, manual wiring | Keep the initial utility small; introduce adapters without speculative module or DI infrastructure. |
| ADR-007 | Hardware feasibility gate first | Watch grouping, retention and vibration cannot be proven by unit tests. Require ONE/TWO/THREE acceptance before production relay expansion. |
| ADR-008 | Conservative, evidence-led cleanup | Telegram removal/replacement is not guaranteed read state. Avoid deleting unread watch entries prematurely. |
| ADR-009 | Garmin Connect mirrors Android notifications | No direct Bluetooth or Garmin API integration is needed for the initial approach. |
| ADR-010 | Apache-2.0 license | Use the license proposed in the agreed open-source plan. |
| ADR-011 | Minimum API 29 remains provisional | Start at Android 10 and revisit only with compatibility evidence. |
| ADR-012 | No automatic cleanup: relay entries live until the user or the system removes them | Owner evidence R1–R3 (2026-10-13, VALIDATION.md): removing Telegram's phone notification did not remove relay entries (phone or watch), so Telegram removal is not read evidence and must not trigger cleanup (ADR-008 upheld). User-initiated dismissal and system clear-all remove relay entries and propagate to the watch via mirroring, which is the intended explicit-cleanup path. Implementing no automatic policy removes the last risk of deleting unread watch entries and adds no code. Only Telegram (ADR-003) unverified-invariants note: if a future device check shows Telegram removal masking unread messages, the evidence policy is revisited. |
| ADR-013 | Memory audit target re-scoped to the measured Compose/Kotlin floor | Measured listener-only steady state on a stock Android device (2026-10-13, owner UI closed): private PSS 31.4MB / TOTAL PSS 50.3MB (shared system 18.9MB unattributable). The planning-time "<30MB normal memory" target predates the Compose choice: the stack floor (~14MB attributed code + ~6MB framework "Private Other" + runtime heaps) exceeds it while the relay's own state stays negligible. Options considered: leaner UI stack (real redesign, rejected) or record honestly and re-scope the target to "document the measured floor; relay-specific overhead near zero" (owner chose A, 2026-10-13). Remaining atom targets (processing <20ms, battery <1%/day, zero network) met with headroom; no exemption for anything else. If a future audit materially shifts the floor, new evidence re-opens this decision. |

## Superseded proposals

The earlier discussion considered updating one notification per conversation and testing both notification strategies. The later user decision supersedes those proposals: retain separate notifications for every message. The latest plan governs implementation.

## Future changes

Append a dated decision with evidence, alternatives, consequences and the superseded ID. Do not silently rewrite the product contract when a device limitation appears; record the limitation and reassess feasibility.
