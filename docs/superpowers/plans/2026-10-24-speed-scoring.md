# Speed Scoring Implementation Plan

**Goal:** Award more points for faster correct answers while preserving the existing UI and server authority.

**Architecture:** Record the Server's monotonic acceptance time for the first valid answer. At question close, award an integer 50–100 points for correct answers using `50 + round(50 * remaining / duration)`; incorrect, missing and late answers receive zero. Reuse the stored award in result events, cumulative ranking and persisted history.

**Tech Stack:** Java 21, JUnit 5, TLS gateway, SQLite, JavaFX 21.

**Spec:** User request dated 2026-10-24: faster answers should earn higher scores. Keep the current 100-point maximum and the established UI/UX.

## Constraints and review focus

- Only Server timing controls score; ignore client timestamps and claimed points.
- Rejected or duplicate answers must not replace the original answer or its timestamp.
- Reset per-question awards/timing on each question and new match.
- Publish correctness/awards only when the question closes.
- Preserve ties, history schema, existing saved matches, UI and result payload fields.

### Task 1: Scoring behavior

**Files:** `nqb-server/src/main/java/vn/edu/hutech/nqb/server/SpeedScoring.java`, `QuizServer.java`; corresponding `SpeedScoringTest.java` and `ServerIntegrationTest.java`.

**Interface:** `SpeedScoring.points(boolean correct, long elapsedNanos, long durationNanos)` returns an integer award, with zero for incorrect or out-of-window answers.

- [x] Write a TLS integration test: faster correct > slower correct despite forged client timing; duplicate and wrong answers do not change awards; next-question timeout does not reuse timing; final scores equal persisted history.
- [x] Run the test against fixed scoring and observe failure.
- [x] Add deterministic policy tests: 10 seconds gives 95/75/55 points at 1/5/9 seconds; immediate gives 100, just-before-deadline gives 50; at/after deadline and wrong/missing answers give zero; relative timing works at 5 and 120 seconds.
- [x] Implement the scoring policy and record first accepted Server timing. Clear round timing/points on question open, start and room reset.
- [x] Return stored per-round awards in `QUESTION_RESULT`; update cumulative score only at close.
- [x] Update existing fixed-score assertions without weakening duplicate/late-answer checks; run the full Maven suite.

### Task 2: Player feedback and verification

**Files:** `ArcadeApplication.java`, `LiveLanUiCheck.java`, current architecture/protocol/context documentation and `docs/Cham_diem_theo_toc_do.md`.

- [x] Add a short scoring-rule hint in the existing lobby.
- [x] In desktop QA, delay a correct answer and verify the displayed award equals Server `points` and is below 100.
- [x] Run desktop QA using temporary TLS/SQLite data and inspect the result screen.
- [x] Update current documentation to replace the fixed-score rule and record verification results.

## Verification results

- Observed the new integration test fail on fixed scoring, then pass with variable awards.
- Full offline Maven install: 103 tests, zero failures/errors.
- JavaFX desktop QA at 1024×640: `COMMANDER_UI_PASS`, `QUESTION_IMPORT_UI_PASS`, `LIVE_LAN_UI_PASS`.
- Rendered-cell inspection must run after ScrollPane skin/layout attachment; moved the result snapshot before assertions. No production rendering change was needed.
- Reviewed real lobby/result snapshots. The deliberately delayed second correct answer visibly receives **+88 điểm**, matching Server points. Screenshots: `output/speed-scoring-qa/`.
