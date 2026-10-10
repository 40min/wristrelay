# Dart task index

Board: **General/WristRelay**. Created 2026-10-10. All tasks start **To-do**; the skeleton is initial scaffolding, not completion of the feasibility prototype. Dart uses `Project` + `epic` tag for phase parents and `Subtask` for children. Workspace default assignee is Andrei Sorokin.

Phase 0 is critical. Phase 1 foundation can run alongside it. Phase 2 waits for 0 and 1; later epics follow parser → relay → Garmin/reliability → audit → release.

## [Epic 0 — Feasibility prototype](https://app.dartai.com/t/xxjkAHX99hrO-Epic-0-Feasibility-prototype) — xxjkAHX99hrO

| ID | Task |
|---|---|
| KPKZ7uR5XcIt | [0.1 — Complete the minimal permission and debug screen](https://app.dartai.com/t/KPKZ7uR5XcIt-0-1-Complete-the-minimal-permi) |
| KhXdqpB8JXZg | [0.2 — Inspect Telegram notification structures](https://app.dartai.com/t/KhXdqpB8JXZg-0-2-Inspect-Telegram-notificat) |
| K8VRXHNF0stM | [0.3 — Prove extraction of three distinct messages](https://app.dartai.com/t/K8VRXHNF0stM-0-3-Prove-extraction-of-three) |
| jCtZshOdvjRb | [0.4 — Publish three independent prototype notifications](https://app.dartai.com/t/jCtZshOdvjRb-0-4-Publish-three-independent) |
| JxxZQZQhejGm | [0.5 — Configure Garmin Connect application filtering](https://app.dartai.com/t/JxxZQZQhejGm-0-5-Configure-Garmin-Connect) |
| P6XBpU1Yjg6O | [0.6 — Verify Garmin feasibility and record Go/No-Go](https://app.dartai.com/t/P6XBpU1Yjg6O-0-6-Verify-Garmin-feasibility) |

## [Epic 1 — Project foundation](https://app.dartai.com/t/EV2ECAQt2Ypz-Epic-1-Project-foundation) — EV2ECAQt2Ypz

| ID | Task |
|---|---|
| 3fue9RbNwuhg | [1.1 — Harden foundation and source permission gates](https://app.dartai.com/t/3fue9RbNwuhg-1-1-Harden-foundation-and) |
| FIiG1HGK95B3 | [1.2 — Complete CI, static analysis and test infrastructure](https://app.dartai.com/t/FIiG1HGK95B3-1-2-Complete-CI-static-analysi) |
| F4hXZ07oI2ik | [1.3 — Maintain project and developer documentation](https://app.dartai.com/t/F4hXZ07oI2ik-1-3-Maintain-project-and-devel) |

## [Epic 2 — Telegram parser](https://app.dartai.com/t/YrsA8YBt8mlj-Epic-2-Telegram-parser) — YrsA8YBt8mlj

| ID | Task |
|---|---|
| p6Re6Be9qdNK | [2.1 — Implement Telegram source recognition and normalized parsing](https://app.dartai.com/t/p6Re6Be9qdNK-2-1-Implement-Telegram-source) |
| WxeHzXOnyp1L | [2.2 — Cover parser variants, updates and identical texts](https://app.dartai.com/t/WxeHzXOnyp1L-2-2-Cover-parser-variants) |

## [Epic 3 — Message detection and relay](https://app.dartai.com/t/DIuxlwH1H2wl-Epic-3-Message-detection-and) — DIuxlwH1H2wl

| ID | Task |
|---|---|
| jfWjihNuYw9U | [3.1 — Implement snapshot tracking and bounded deduplication](https://app.dartai.com/t/jfWjihNuYw9U-3-1-Implement-snapshot-trackin) |
| PCogbWZEQktK | [3.2 — Implement independent notification publication and IDs](https://app.dartai.com/t/PCogbWZEQktK-3-2-Implement-independent) |
| c3EV0zwONI9Q | [3.3 — Verify relay event sequences and resource bounds](https://app.dartai.com/t/c3EV0zwONI9Q-3-3-Verify-relay-event-sequenc) |

## [Epic 4 — Garmin integration and settings](https://app.dartai.com/t/op5hCSzZJFjh-Epic-4-Garmin-integration-and) — op5hCSzZJFjh

| ID | Task |
|---|---|
| NjKl8Z8aCRGL | [4.1 — Implement persistent settings and setup guide](https://app.dartai.com/t/NjKl8Z8aCRGL-4-1-Implement-persistent-setti) |
| RBUH1LoKzqpL | [4.2 — Validate channel, vibration, text and browsing on Garmin](https://app.dartai.com/t/RBUH1LoKzqpL-4-2-Validate-channel-vibration) |
| beBKeJrznXdq | [4.3 — Validate Bluetooth reconnect and application filtering](https://app.dartai.com/t/beBKeJrznXdq-4-3-Validate-Bluetooth-reconne) |

## [Epic 5 — Lifecycle and reliability](https://app.dartai.com/t/ONVMdbILvl7s-Epic-5-Lifecycle-and-reliabili) — ONVMdbILvl7s

| ID | Task |
|---|---|
| nuAAwlyEiQDj | [5.1 — Define and implement conservative notification cleanup](https://app.dartai.com/t/nuAAwlyEiQDj-5-1-Define-and-implement-conse) |
| hesC7qy3OKiY | [5.2 — Handle listener reconnect, restart and permission revocation](https://app.dartai.com/t/hesC7qy3OKiY-5-2-Handle-listener-reconnect) |
| tyqlhylGMe3E | [5.3 — Run daily-use reliability acceptance matrix](https://app.dartai.com/t/tyqlhylGMe3E-5-3-Run-daily-use-reliability) |

## [Epic 6 — Security and performance audit](https://app.dartai.com/t/lRmAx62HsoZ5-Epic-6-Security-and-performanc) — lRmAx62HsoZ5

| ID | Task |
|---|---|
| SHGI5R611xi7 | [6.1 — Audit permissions, components, dependencies and data flow](https://app.dartai.com/t/SHGI5R611xi7-6-1-Audit-permissions-componen) |
| B51JFL8TFuAw | [6.2 — Profile latency, memory, idle and battery behavior](https://app.dartai.com/t/B51JFL8TFuAw-6-2-Profile-latency-memory) |

## [Epic 7 — Packaging and public release](https://app.dartai.com/t/MnTSYKvo1H5x-Epic-7-Packaging-and-public) — MnTSYKvo1H5x

| ID | Task |
|---|---|
| JXR3W2Yzxknf | [7.1 — Prepare controlled release builds and checksums](https://app.dartai.com/t/JXR3W2Yzxknf-7-1-Prepare-controlled-release) |
| 9PoJ3ZEG31JE | [7.2 — Finalize user docs, support limits and release acceptance](https://app.dartai.com/t/9PoJ3ZEG31JE-7-2-Finalize-user-docs-support) |
