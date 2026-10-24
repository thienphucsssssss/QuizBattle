# Management Sidebar Implementation Plan

**Goal:** Complete the existing left sidebar and implement the additional management features shown in the user's second reference image.

**Architecture:** Keep the JavaFX BorderPane shell and navy/lime design. Add focused management views driven by the existing TLS gateway and room events; preserve the selected management page during a live match. Add only backward-compatible manager telemetry to room snapshots.

**Tech Stack:** Java 21, JavaFX 21, Maven, JUnit 5, TLS sockets, SQLite.

**Spec:** User request dated 2026-10-24 and `.cursor/rules/preserve-existing-ui-ux.mdc`.

## Global Constraints

- Existing UI/UX remains the design foundation; screenshots supply feature references.
- Real server data only; show measured ping without inventing link speed.
- User management remains Admin-only; students retain their existing play flow.
- No database migration, sample runtime data, or changes to local credentials/configuration.

## Review Focus

- Game events must not pull managers away from their selected page.
- Answer telemetry must not reveal choices/correct answers to students.
- Empty rooms, disconnects and reopened rooms must clear stale results.
- Minimum window 1024×640 must keep navigation and logout reachable.
- Late responses must not update detached views or a new login session.

### Task 1: Manager room telemetry

**Files:** `nqb-server/src/main/java/vn/edu/hutech/nqb/server/QuizServer.java`; corresponding `ServerIntegrationTest.java`.

- [x] Add a failing TLS integration test for per-player submitted/score fields visible only to managers, no answer leakage, and reset clearing telemetry.
- [x] Run the targeted test and observe failure.
- [x] Extend room state with manager-only player telemetry, current question number and published ranking; preserve existing protocol fields.
- [x] Rerun the test.

### Task 2: Sidebar and feature workspaces

**Files:** `ManagementSidebar.java`, `CommanderViews.java`, `ArcadeApplication.java`, `ui/arcade.css`; reuse `ManagementViews.java` unchanged.

**Interfaces:** Sidebar accepts role, display name and route callback; workspaces consume `ROOM_STATE`, `QUESTION_LIST`, `HISTORY_LIST`, game events and existing app commands.

- [x] Extend `LiveLanUiCheck` to exercise navigation, overview data, empty states, live monitoring, arena search, settings and role restrictions.
- [x] Run desktop QA and observe missing-feature failure.
- [x] Add vector icons, scrollable navigation, connection/ping and account footer within the existing visual design.
- [x] Implement overview, monitor, arena and settings entry points; reuse existing bank/room/history/password flows.
- [x] Preserve selected management routes on game events and stop connection timers on logout/disconnect.
- [x] Run all Maven tests and desktop QA at 1366×768 and 1024×640; inspect actual screenshots.

### Task 3: Delivery

- [x] Document available features, verification and LAN test limitations.
- [x] Review diff and report changed files without committing unrelated user work.

## Verification results

- Full offline Maven install: 96 tests, zero failures/errors.
- Desktop QA at both requested sizes: `COMMANDER_UI_PASS`, `QUESTION_IMPORT_UI_PASS`, `LIVE_LAN_UI_PASS`.
- Added a rendered-cell regression assertion after screenshots revealed phase-dependent table values were stale. Observed its failure, added table refresh, and verified the rendered status through active/final phases.
- Real JavaFX snapshots reviewed for overview, monitoring, filtered arena and minimum-window navigation. Screenshots: `output/sidebar-final/`.
