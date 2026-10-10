# WristRelay contributor guidance

Read README.md and docs/PLAN.md, docs/DECISIONS.md and docs/PRIVACY.md before implementing behavior.

- One genuinely new message creates one independent notification; never overwrite previous message entries or deduplicate by text alone.
- Never cancel, modify or suppress original Telegram notifications. Garmin Connect app filtering is owner setup.
- No INTERNET, analytics, persistent message history, message-body logs, polling or permanent foreground service.
- Filter explicitly enabled sources early; ignore WristRelay itself. Keep caches bounded.
- Phase 0 physical Garmin acceptance gates production relay expansion. Cleanup policy needs source-removal evidence.
- Preserve unrelated changes. Keep one module and avoid speculative placeholder abstractions.
- Validate relevant changes with ./gradlew assembleDebug lintDebug testDebugUnitTest using JDK 17+ and Android SDK 37.0. Distinguish absent unit tests from passing coverage.
- Update docs for actual behavior and limitations. Device actions and release signing are separate authorized work.
