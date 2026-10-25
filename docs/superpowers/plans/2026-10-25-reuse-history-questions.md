# Reuse questions from match history

**Goal:** A host can select a saved match, open its score dialog and use its questions to configure another room.

**Architecture:** Persist the actual played question snapshots and duration together with the match in a version-3 SQLite migration. ROOM_CONFIGURE accepts an owned history_session_id as an alternative question source; START_GAME reads only that stored source. The existing JavaFX room form is prefilled, and saving from a finished room resets its players to a fresh waiting round only after validation.

**Tech stack:** Java 21, JavaFX 21, JDBC SQLite, existing TLS protocol and JUnit tests.

## Constraints and review focus

- Keep the established history → score dialog → room configuration flow and cobalt/lime presentation.
- Preserve existing accounts, banks and scores during migration. Old matches without snapshots remain readable and explain why reuse is unavailable.
- Check ownership on the Server; never expose answers or snapshots through public room/history metadata.
- Replayed questions retain their original text/options/answers even after bank edits or deletion. Do not insert duplicate questions into the bank.
- Enforce source size, room ownership and phase guards before changing room state; reset retains connected players and clears readiness/scores.

## Implementation

- [x] Add and run a failing real TLS integration test for saved metadata and reuse of an exact question snapshot after source deletion.
- [x] Add atomic schema migration and match snapshot persistence, with restart/legacy/rollback/ownership coverage.
- [x] Add the history source to room configuration/start/reset and test phase, ownership, source conflicts and size limits.
- [x] Add the score-dialog action and prefilled room form using native existing controls; test actual reuse and cancel behavior in LiveLanUiCheck.
- [x] Run the Maven suite and desktop QA at 1024×640 and 1366×768, inspect snapshots, and update protocol/design notes.

## Verification notes

- The new integration test failed on the old Server because completed matches did not retain a reusable question source. The new UI assertion failed before the reuse action was added. Both behaviors now pass.
- Maven reactor install (including the complete test suite): 134 tests, zero failures/errors/skips. Focused persistence/integration tests cover legacy migration, migration rollback, atomic result/snapshot writes, source ownership, edits/deletion, restart, phase/size guards, and reuse from a finished room.
- The first extended live QA exposed a hardcoded pre-existing overview expectation of one match. The replay now creates a second saved match, so QA uses the count before its own test and verifies the exact one-match increment afterward.
- Final live QA passes at both 1024×640 and 1366×768 through the full real TLS/SQLite flow, including source/count/duration defaults, cancel without mutation, size validation and actual replay gameplay. Component QA passes both supported sizes, all ties, empty/single-player results, visible actions and a disabled action/explanation for legacy matches. Final logs: `output/history-reuse-final-1024.log`, `output/history-reuse-final-1366.log`, `output/history-reuse-components-final.log`; snapshots were written to `../output/history-reuse-final` and `../output/history-reuse-components-final` relative to the repository root.
- Old matches contain no question snapshots and cannot be reconstructed accurately. They remain readable; reuse is intentionally unavailable. Runtime needs the updated Server and Client; no personal database or active user Server was modified by QA.
