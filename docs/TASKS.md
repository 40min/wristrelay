# Dart task index

Board: **General/WristRelay**. Simplified 2026-10-12 to 4 epics and 12 tasks (formerly 7 epics and 25 subtasks; the originals were merged, with all acceptance criteria preserved in task descriptions, and moved to Dart trash). Dart uses `Project` + `epic` tag for phase parents and `Subtask` for children. Workspace default assignee is Andrei Sorokin.

Epic 0 is the critical gate and blocks production relay work (encoded as a Dart blocker on task 2.1). Epic 1 foundation can run alongside it. Order: 0 → 1 → 2 → 3.

## [Epic 0 — Feasibility prototype](https://app.dartai.com/t/LFi26tTXgteC-Epic-0-Feasibility-prototype) — LFi26tTXgteC

| ID | Task |
|---|---|
| la1tnqWsKF0j | [0.1 — Permission and debug screen, Telegram metadata inspection](https://app.dartai.com/t/la1tnqWsKF0j-0-1-Permission-and-debug-scree) |
| qWeHOuyXEqRm | [0.2 — Prove extraction and publish three independent notifications](https://app.dartai.com/t/qWeHOuyXEqRm-0-2-Prove-extraction-and-publi) |
| ZshNc5c4dPij | [0.3 — Configure Garmin Connect filtering and record Go/No-Go](https://app.dartai.com/t/ZshNc5c4dPij-0-3-Configure-Garmin-Connect) |

## [Epic 1 — Project foundation](https://app.dartai.com/t/166EzKR0vU4S-Epic-1-Project-foundation) — 166EzKR0vU4S

| ID | Task |
|---|---|
| Of5f0qeZgN19 | [1.1 — Harden foundation and source permission gates](https://app.dartai.com/t/Of5f0qeZgN19-1-1-Harden-foundation-and) |
| olBGwxXFZDaK | [1.2 — Complete CI, static analysis and test infrastructure](https://app.dartai.com/t/olBGwxXFZDaK-1-2-Complete-CI-static-analysi) |

## [Epic 2 — Telegram relay and daily-use reliability](https://app.dartai.com/t/nUquNCr0F77y-Epic-2-Telegram-relay-and) — nUquNCr0F77y

| ID | Task |
|---|---|
| Za9e18ntRGNK | [2.1 — Implement Telegram parser with variant fixture coverage](https://app.dartai.com/t/Za9e18ntRGNK-2-1-Implement-Telegram-parser) |
| IikqzDaq3RAw | [2.2 — Implement detection, independent publication and ID management](https://app.dartai.com/t/IikqzDaq3RAw-2-2-Implement-detection-indepe) |
| 1KsiFTlHuVvi | [2.3 — Implement persistent settings and setup guide](https://app.dartai.com/t/1KsiFTlHuVvi-2-3-Implement-persistent-setti) |
| XSDRAuPOb4mD | [2.4 — Implement cleanup, reconnect handling and reliability matrix](https://app.dartai.com/t/XSDRAuPOb4mD-2-4-Implement-cleanup-reconnec) |

## [Epic 3 — Garmin validation, audit and release](https://app.dartai.com/t/9Wg96GPUEvBW-Epic-3-Garmin-validation-audit) — 9Wg96GPUEvBW

| ID | Task |
|---|---|
| uOR5w1ntEE9d | [3.1 — Validate watch presentation, reconnect and app filtering](https://app.dartai.com/t/uOR5w1ntEE9d-3-1-Validate-watch-presentatio) |
| Ic9YTglOwQbm | [3.2 — Audit security and performance](https://app.dartai.com/t/Ic9YTglOwQbm-3-2-Audit-security-and-perform) |
| ZmiayPuFgdjE | [3.3 — Prepare controlled release and finalize user docs](https://app.dartai.com/t/ZmiayPuFgdjE-3-3-Prepare-controlled-release) |
| htkZ8SyW954a | [3.4 — Sanitize repository docs of personal/identifying data before public release](https://app.dartai.com/t/htkZ8SyW954a-3-4-Sanitize-repository-docs) |
| gTVd1XgrDfEn | [3.5 — Consumer-friendly UI: minimal controls, icon-theme colors, debug capture out of the consumer surface](https://app.dartai.com/t/gTVd1XgrDfEn-3-5-Consumer-friendly-UI-minim) |

Board closure state (2026-10-13): Epics 0–2 done; Epic 3 subtasks 3.1/3.2 done with evidence comments; 3.3 deferred to a new session pending owner decisions (release signing location; device version numbers for release docs); 3.4 and 3.5 are new pre-release hygiene/design tasks — 3.4 must complete before any public release.
