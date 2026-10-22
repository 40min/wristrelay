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

## ADR-014 — Optional original dismissal and supplied actions (2026-10-22)

Owner authorization supersedes only ADR-003's absolute prohibition on dismissing originals. Preservation remains the default; an independent persisted `hideTelegramOriginals=false` option permits dismissing only a supported Telegram message snapshot after all its fresh copies are confirmed active. There is no sweep, restoration, summary dismissal or relay cleanup. Garmin filtering remains owner setup. The rejected alternative was always hiding originals, which would change existing behavior without opt-in.

Copies reuse source-owned valid action PendingIntents/RemoteInputs and content intent; creator package must match Telegram. No delete intent or custom send handler is forwarded. Action lifetime and conversation/latest-message semantics remain Telegram's responsibility. Dismissal can invalidate tokens and cannot prevent an initial Telegram alert.

Confirmation is event-driven using own notification callbacks plus active-entry checks, bounded content-free key/post-time/notification-ID metadata. Own cancellation starts a fresh series after its removal callback, or a distinct post-time repost before removal; delayed old removal cannot reset that newer series. Same-key/same-post-time indistinguishable callbacks before removal cannot reliably identify a new message without a stable source message ID. Physical action, rapid-arrival and lifecycle acceptance remains open.

## ADR-015 — Dismiss the explicitly acted-on copy after handoff (2026-10-22)

Owner device evidence: supplied Reply sends and Mark as read executes, but the copied notification remains. The owner requests removal of that copy after Mark as read or submitting Reply, independently of Hide Telegram originals. This adds a narrow explicit-user-action exception to ADR-012; automatic source-removal cleanup remains disabled. Opening/canceling reply composition does not count.

Wrap non-activity actions in a nonexported receiver, retain Telegram ownership/RemoteInput metadata, and remove only the originating still-matching active copy after Android accepts the original token handoff. Expired/rejected tokens leave the copy. No conversation sweep, other-copy deletion, or original Telegram cancellation occurs. Random per-action identity guards against numeric notification-ID reuse. Activity tokens stay direct to avoid activity trampolines, so their reply/send completion cannot be observed and their copies remain. Rejected alternatives: auto-cancel (content-click semantics only), cleanup on source removal (not read evidence), and conversation-wide cancellation (would discard independent unread entries).

Successful Android handoff is not Telegram server delivery confirmation. Phone-copy removal may remove its mirrored watch copy. Updated device acceptance remains open; the owner evidence above predates this fix.

Action cleanup recognizes submitted RemoteInput actions and semantic Mark as read. For the owner's English Telegram setup, an exact trimmed case-insensitive “Mark as read” label is also accepted when semantic metadata is absent. Unknown/localized actions without recognized semantic metadata, and no-input Reply/compose-opening actions, retain their original direct tokens and do not clean up copies. Inline wrappers require actual text or data results for one of their declared RemoteInput keys before handing off or dismissing; a missing/mismatched submission leaves the copy intact. Only result-key metadata is kept in the system-held token, never submitted text.
