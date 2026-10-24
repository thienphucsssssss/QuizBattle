# Question Bank Polish Implementation Plan

**Goal:** Improve the existing question bank using the supplied reference for filter grouping, readable question previews and answer detail hierarchy.

**Architecture:** Keep the native JavaFX TableView, checkbox state, private question APIs and dialogs. Render summary rows as compact question previews; use QUESTION_GET for the full inspector. Scope CSS to the bank so other management screens retain their layout.

**Tech Stack:** Java 21, JavaFX 21, existing CSS and Be Vietnam Pro.

**Spec:** User request dated 2026-10-25 and `codex-clipboard-e2cf2ae8-2847-46cc-bc2e-e539c668fa99.png`; existing UI preservation rule in `.cursor/rules/preserve-existing-ui-ux.mdc`.

## Constraints and design

- Keep navy sidebar, cobalt actions and lime emphasis; reference image informs hierarchy rather than replacing the project identity.
- Tokens: ink #202146, workspace #F4F6FC, surface #FFFFFF, cobalt #3049D5, lime #C5F45B, secondary text #66728A.
- Use existing font: 28px page title, 16px semibold question previews, 13px metadata, 17px full question and 14px answers.
- Layout: heading/import/add → grouped filters → set/selection tools → preview list + inspector → pagination. Align content left; stack header actions and use the existing detail dialog at narrow widths.
- Use actual category, active status, timestamp and answers only. No backend, database, scoring or new metadata changes.
- Keep cross-page checks independent from the focused detail row; retain import/templates, set management, filters, sorting and all CRUD actions.

## Review focus

- Long Vietnamese questions/options wrap in the inspector; summary text stays bounded in virtualized rows.
- At 1024×640, filters and pagination remain usable and full details open in the dialog.
- At 1366×768, the right inspector shows the four actual options and exactly one correct answer.
- Rapid row/filter changes cannot render a stale inspector; empty results clear its previous question.
- Checking 20 across pages, clearing checks and account changes retain existing behavior.

### Task 1: Presentation and interactions

**Files:** `ManagementViews.java`, bank-scoped styles in `arcade.css`, `LiveLanUiCheck.java`.

- [x] Extend live QA to verify all four actual answer texts, the correct-answer marker and independent checkbox state in wide/narrow detail views; observe the expected failure.
- [x] Add compact preview cells, responsive header, grouped filters, selection tools and a full-answer inspector. Keep existing API payloads and permission checks.
- [x] Preserve async guards and clear stale details on empty selection; use existing dialogs for narrow layouts.

### Task 2: Validation and documentation

- [x] Run the full Maven suite and install the updated artifacts.
- [x] Run live UI QA at 1024×640 and 1366×768; inspect previews, full details and empty states; repair clipping if observed.
- [x] Update design/use notes and the verified build stamp. Leave the user's running Server, database and Client sessions intact.

## Verification

- Full Maven install: 126 tests, zero failures/errors/skips (`tmp/bank-polish-final-build.log`).
- Both live UI sizes pass presentation, checkbox pagination, import, set gameplay, management and LAN workflows (`tmp/bank-polish-final-1024.log`, `tmp/bank-polish-final-1366.log`).
- Inspected realistic Vietnamese previews, long answer wrapping, correct answer emphasis, empty filters and scrollable detail actions. Removed a duplicate dialog heading and rechecked the final narrow dialog.
- Snapshots: `output/bank-polish-final/1024/` and `output/bank-polish-final/1366/`.

Execution: implement directly in this chat under the user's request. No additional approval or separate chat is needed.
