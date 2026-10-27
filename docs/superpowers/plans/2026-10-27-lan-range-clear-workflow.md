# Clear LAN range workflow implementation plan

> Execution: native implementation in the current session. No delegation, commits, or approval gate is needed for the requested UI update.

**Goal:** Make manual field measurements understandable, with live room connections separate from the one laptop connection being measured.

**Architecture:** Retain LanRangeClient, the protocol and role permissions. Recompose LanRangeView into live connections, visible required preparation fields, and run-specific results. Collapse secondary details/history and the preparation form during a run; keep actions outside scrolling content.

**Tech Stack:** Java 21, JavaFX 21, existing FxKit and arcade.css.

**Spec:** docs/Do_tam_ket_noi_LAN.md and the user's request to make this feature easier to understand.

## Design decisions

- Palette: ink #202146, cobalt #3049D5, lime #C5F45B, canvas #F4F6FC, surface #FFFFFF, line #D7DDEB.
- Type: existing Be Vietnam Pro families. Plain Vietnamese labels, 12–14px supporting text and a 27px page heading; the entered marker is the primary number.
- Layout: left aligned, live Server / Client identity row; preparation with required fields visible and a short field-test guide; run results underneath. Extra network metadata and saved history expand on demand.
- Distinguishing feature: the measured marker and the identity of the measured laptop are explicit. Joined clients never acquire another laptop's statistics.
- Review: replace the former all-at-once dense dashboard with the actual preparation → measure → review sequence. Keep the existing sidebar, room page and native controls.

## Global constraints

- Metres are entered after physical tape measurement; no RTT-to-distance inference or runtime fixtures.
- One run belongs to the local client connection. No remote start or aggregate monitoring is introduced.
- Server stays left, Client right; show only real, authorized socket/roster information.
- Unknown reverse evidence stays unknown; local tests do not count as field range.
- Preserve existing in-progress work and backend behavior. Support 1366×768 and 1024×640, keyboard focus, fixed actions and student dialog.

## Review focus

- Roster changes during history viewing: live IP changes must not relabel historical metrics.
- A manager with several students: identify the manager laptop's own measured connection.
- Missing server summary: retain incomplete explanation and no invented successes.
- Running and completed states: keep form values and controls consistent across navigation.
- Small windows, long names and many peers: scrolling must keep actions and all identities reachable.

### Task 1: Clear identity and workflow

**Files:** LanRangeView.java, arcade.css, LanRangeStatesUiCheck.java, LanRangeUiCheck.java.

- [x] Add UI coverage for independent live vs run endpoints, including roster changes and student privacy. Observe the new contract fail before implementation.
- [x] Move live addresses/roster out of statistics; explicitly show the local laptop and run-specific endpoints.
- [x] Show required name/route/marker/duration fields, concise steps, collapsed optional settings/history, and empty results until a run exists. Collapse preparation during an active run.
- [x] Verify fixtures for pass/incomplete/local states and a real TLS run with join/rejoin, tab switching, stop/completion, CSV and both window sizes. Inspect screenshots.

### Task 2: Documentation and regression

**Files:** docs/Do_tam_ket_noi_LAN.md, design-system/network-quiz-battle/MASTER.md.

- [x] Update operating instructions and design decisions to match the final UI.
- [x] Run the full Maven verification suite and git diff --check. Report genuine limitations and deliver a screenshot of the actual implementation.

## Verification

- RED: LanRangeStatesUiCheck failed because the manager's own measured laptop was confused with joined students.
- GREEN: live/historical endpoint separation, student roster privacy, unknown Server evidence and field/local result classification pass.
- Maven verify: 178 tests, zero failures/errors; all six reactor projects successful. Log: tmp/lan-clear-verify.log.
- Real TLS UI check: JOIN/rejoin/disconnect, invalid input, both directions, stop/reopen, full 30-second completion, CSV, both viewport sizes and student dialog. Screenshots/logs: tmp/lan-clear-ui/ and tmp/lan-clear-ui.log.
- Tests ran locally on one physical machine. No physical range claim or automatically measured metres.
