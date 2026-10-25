# History and ranking polish implementation plan

**Goal:** Improve the existing history table and score dialog using selected details from the supplied reference while preserving QuizBattle's UI and workflow.

**Architecture:** Keep ManagementViews responsible for history requests and pagination. Reuse VictoryPodium and RankBoard in a focused HistoryResults view. All results, competition ranks and ties remain Server-authoritative.

**Tech stack:** Java 21, JavaFX 21, existing bundled CSS and Be Vietnam Pro fonts.

**Spec:** User's reference image, AGENTS.md, .cursor/rules/preserve-existing-ui-ux.mdc, design-system/network-quiz-battle/MASTER.md.

## Constraints

- Preserve sidebar, cobalt/lime identity, native table, pagination and select-match/open-dialog workflow.
- No backend, scoring, protocol or account changes; no invented speed, streak or answer-distribution statistics.
- Do not overwrite unrelated work already present in the checkout.
- Support 1024×640 scenes, long Vietnamese names, missing results and tied ranks.

## Review focus

- Every tied winner must remain visible, including more than three players with rank <= 3.
- A late response must not open a dialog after navigating away.
- Scroll content rather than hiding the dialog close button on small displays.
- Scores and correct counts must match stored values; missing values must not become invented statistics.
- Loading and errors must leave a usable way to retry.

## Task 1: History browsing and result presentation

Files: ManagementViews.java, new HistoryResults.java, RankBoard.java, arcade.css; focused opt-in HistoryResultsUiCheck.java and existing LiveLanUiCheck.java.

- [x] Add a visual/behavioral QA case for opening a stored match, preserving ranks/names/scores and ties, plus empty/long/compact component cases. Run against the old UI to establish the missing result presentation.
- [x] Polish the existing table with readable local timestamps, room metadata, numeric badges, clear loading/error/empty states and existing footer actions.
- [x] Add a scrollable score dialog that reuses the podium, avatars and ranking rows, with match metadata and exact correct-answer counts.
- [x] Refine ranking row hierarchy with a labeled own-player badge, secondary answer count where supplied, score units and accessible text. Preserve motion and game layout behavior.
- [x] Run focused JavaFX QA at 1366×768 and 1024×640; inspect actual Scene snapshots and correct clipping or hierarchy problems.

## Task 2: Verification and project notes

- [x] Run Maven reactor verify/install and the real TLS/SQLite LiveLanUiCheck at both supported sizes.
- [x] Update the design-system note to describe only the shipped changes.
- [x] Review the final diff; report verification and any remaining limitations.

## Verification results

- The added live-history assertion failed on the old dialog because the shared podium and full result view were missing; it passes with the new presentation.
- Maven reactor `verify`: 128 tests, zero failures/errors/skips. Final artifacts built and installed successfully.
- `HistoryResultsUiCheck`: passed both viewport sizes, tied ranks including ten winners, long names, empty/single-player results, complete compact podium, no horizontal overflow and reachable close action.
- `LiveLanUiCheck`: passed at 1366×768 and 1024×640 through the real TLS/SQLite flow. Scene snapshots were visually reviewed; final 1024 screenshots are in `../output/history-ranking-final/1024` and final component screenshots in `../output/history-ranking-components-final`.
- A rerun exposed an existing render-timing race in the arena QA: the room phase could update before TableView cells were recreated. The test now waits for the visible cell text, still with the existing bounded timeout; production arena behavior was not changed. The complete final live QA passes.
- Existing JavaFX classpath and POI logging-provider diagnostics remain non-blocking. Local QA uses temporary data and does not demonstrate multiple physical LAN machines.
