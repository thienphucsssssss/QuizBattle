# Room Question Sets Implementation Plan

**Goal:** Select a named, private question set when configuring a room and restrict gameplay to active questions in that set.

**Architecture:** Add an atomic SQLite v1→v2 migration with question-set/membership tables. Extend the existing authenticated question APIs, import transaction and room state. Reuse the current navy/lime JavaFX dialogs and bank view for set creation/filtering and room selection.

**Tech Stack:** Java 21, JavaFX 21, SQLite, TLS gateway, JUnit 5.

**Spec:** User request dated 2026-10-25, following confirmation that rooms currently sample the entire private bank.

## Constraints and review focus

- Preserve users, questions, history, current styling and existing question import templates.
- A set belongs to one manager; foreign IDs/filters cannot grant access.
- Clients can omit the new field: new rooms use the whole bank and existing rooms retain their configured source.
- Imports associate only successfully committed questions with the requested set; cancel/failure creates nothing.
- Recheck the selected pool at configuration/start; never fall back to unrelated questions.
- Reset retains the selected set; closing the room clears it. An in-use set cannot be deleted.
- Do not apply any other teacher-note requirements from the discussion-only turn.

### Task 1: Persistence, ownership and room selection

**Files:** `QuizStore.java`, `QuizServer.java`, `QuestionImportSession.java`; server migration/integration tests.

**Interfaces:** `QUESTION_SET_LIST`, `QUESTION_SET_CREATE`, `QUESTION_SET_DELETE`; `question_set_id` on room configuration/list filters; optional `question_set_name` on import commit.

- [x] Add failing TLS tests for private sets, filtering, import grouping, selected-pool gameplay, insufficient active questions and reset/delete behavior.
- [x] Observe failure, implement transactional schema upgrade and set operations, then rerun targeted tests.
- [x] Verify v1 users/questions/history survive upgrade and set definitions survive reopening.

### Task 2: JavaFX workflow

**Files:** `QuestionSets.java`, `ManagementViews.java`, `QuestionImportView.java`, `ArcadeApplication.java`.

- [x] Add a bank set selector and management dialog for creating sets from selected rows or current filters, and deleting set membership without deleting questions.
- [x] Default the optional import set name from the file name; append to an existing same-name owned set atomically.
- [x] Add the room question-set chooser, availability summary and validation; show the selected source in the lobby.
- [x] Preserve async guards, focus, existing fields and minimum-window usability.

### Task 3: Verification and documentation

- [x] Extend desktop QA to import a set, filter the bank, select/configure it in a real room and observe only its questions.
- [x] Run full Maven tests and desktop QA, inspect snapshots, and build installed artifacts.
- [x] Document use, schema upgrade and limits. Do not modify live database/configuration or commit unrelated work.

## Verification results

- Offline Maven install: 111 tests, zero failures/errors; all modules built and installed.
- Desktop QA at 1024×640 and 1366×768: `COMMANDER_UI_PASS`, `QUESTION_IMPORT_UI_PASS`, `QUESTION_SET_UI_PASS`, `LIVE_LAN_UI_PASS`.
- Inspected bank filter, import preview, set manager, room chooser, insufficient-pool feedback and selected-set lobby snapshots. The room form scrolls at the minimum size with action buttons visible.
- Logs: `tmp/question-sets-final-build.log`, `tmp/question-sets-final-1024.log`, `tmp/question-sets-final-1366.log`.
- Snapshots: `output/question-sets-final/1024/` and `output/question-sets-final/1366/`.
