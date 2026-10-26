# LAN range workspace Implementation Plan

> **For agentic workers:** Execute the tasks sequentially in this session. The user has requested implementation; no additional approval gate is needed.

**Goal:** Restore the original room lobby and move LAN measurement into its own management navigation tab, using the supplied reference for the measurement interface.

**Architecture:** Reuse LanRangeClient, journals and CSV without protocol changes. LanRangeView provides an embeddable workspace and a student dialog using the same controls; the manager opens the workspace from the sidebar. Restore the large room code and avatar seats without touching the ongoing monitor/launcher changes.

**Tech Stack:** Java 21, JavaFX 21, existing FxKit and navy/cobalt/lime stylesheet.

**Spec:** User request and supplied screenshot on 2026-10-26; docs/Do_tam_ket_noi_LAN.md defines measurement semantics.

## Global Constraints

- Metres are measured manually, never inferred from network timings.
- Keep 30/60 seconds, the existing 95%/1,000 ms criterion and missing-evidence states.
- School Wi-Fi does not require a known AP position; no invented IP, band or range.
- Reference testbench controls and synthetic data belong only to test sources.
- Preserve all unrelated working-tree edits; do not deploy, commit or restart user servers.
- Footer stays reachable at 1366×768 and 1024×640; a same-machine test remains local.

## Review Focus

- Switching tabs while measuring must retain the active run and its entered distance.
- Switching to a saved result must not overwrite or stop a live run.
- A missing server summary must never become a fabricated successful/zero RTT result.
- Disconnect and logout disable starting and clean up view timers/windows.
- Restored room seats must show real JOIN/READY/disconnect states and retain visible room controls.

### Task 1: Pin the requested navigation and real TLS flow

**Files:** nqb-admin-client/src/test/java/vn/edu/hutech/nqb/admin/LanRangeUiCheck.java; RoomTopologyUiCheck.java; LiveLanUiCheck.java.

- [x] Change the LAN UI check to open the sidebar feature, assert an embedded page, navigate away/back during a live run, verify persistent journal/CSV and disconnect at both viewport sizes.
- [x] Run against current production code and observe the missing-tab failure.
- [x] Update the old topology UI check to assert avatar seats, original room code and real ready/disconnect states.

### Task 2: Restore lobby and build the dedicated measurement page

**Files:** ArcadeApplication.java, ManagementSidebar.java, CommanderViews.java, LanRangeView.java, ui/arcade.css.

- [x] Restore the original lobby seats and large room code; remove the topology from the lobby only.
- [x] Add the standalone LAN sidebar route and remove the redundant settings launcher for managers.
- [x] Rebuild measurement controls, manual-distance schematic, direction metrics, progress, farthest passing marker and history around existing real data.
- [x] Keep student access through Options using the same view in a dialog; handle lifecycle/close/export for either host.
- [x] Run the UI checks, inspect snapshots and repair layout/runtime defects.

### Task 3: Verify and document

**Files:** README.md, docs/Do_tam_ket_noi_LAN.md, docs/So_do_ket_noi_phong_thi.md, design-system/network-quiz-battle/MASTER.md, navigation documentation.

- [x] Update entry points and describe the restored lobby and separate LAN workspace.
- [x] Run the full Maven verify and required live UI regression using current classes.
- [x] Review the final diff and report software verification separately from physical-distance acceptance.

## Verification outcome — 26/10/2026

- Full Maven verify: 171 tests, 0 failures, 0 errors, 0 skipped; all six reactor projects succeeded. Installed current JARs locally with tests skipped after verification.
- LAN_RANGE_UI_PASS: real same-machine TLS, invalid metre input, sidebar page without an extra manager dialog, active-run navigation, stop/reopen, full 30-second completion, journal/CSV, disconnect, 1366×768 and 1024×640; student dialog footer also checked.
- ROOM_LOBBY_UI_PASS: real empty room/JOIN/READY/disconnect and large room code restored; 8/10-player fixtures checked at both sizes.
- LAN_RANGE_STATES_UI_PASS: missing reverse evidence remains unknown, incomplete result cannot establish a farthest marker, local results excluded. Fixtures are explicitly marked and never journaled as real measurements.
- LiveLanUiCheck: LIVE_LAN_UI_PASS, COMMANDER_UI_PASS and bank/history/import/question-set/server-compatibility checks passed. Its existing Log4j-provider warning does not prevent those checks; logs are retained.
- Read and visually inspected actual room and measurement snapshots in tmp/restored-room-ui and tmp/lan-workspace-ui. Fixture screenshots are labeled as UI tests.
- Missing-tab check failed before implementation. A second rendering check caught missing reverse evidence being shown as 0/30 in history, and passed after correction.
- No field-distance experiment was performed; real network checks used local TLS. No user server/database, packaged installer, Git commit or unrelated launcher/monitor changes were modified by this task.
