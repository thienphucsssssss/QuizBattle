# Server / Client LAN panels Implementation Plan

> Execute sequentially in this session as requested by the user. Preserve the existing measurement protocol and visual identity.

**Goal:** Display Server and Client as two explicit sides, with the real server endpoint and automatically updated client IPs before a measurement starts.

**Architecture:** Add the requesting connection's socket metadata to authenticated ROOM_STATE snapshots. Reuse the existing role-filtered players[].ip_address for the live client roster. Pass state changes into LanRangeView and retain the actual measurement direction beneath each role title.

**Tech Stack:** Java 21, JavaFX, existing TCP/TLS gateway and JUnit.

**Spec:** User request and attached two-panel screenshot on 2026-10-27.

## Constraints and review focus

- Do not infer, manufacture or resolve IPs from nicknames; only show values supplied from server socket evidence.
- Preserve IP visibility permissions: students learn their own endpoint, not other students' addresses.
- The live roster and the selected measurement are distinct: do not attach one connection's metrics to another student.
- JOIN, leave, reconnect, tab re-entry and controller disconnect must update labels without requiring a measurement.
- Keep historical IPs explicitly attributed to the saved measurement; do not present them as a live connection.
- Keep styling scoped, both viewport sizes usable, existing TCP/TLS/journal/CSV behavior unchanged.

## Tasks

- [x] Add a real-TLS server test proving pre-measurement connection metadata and unchanged peer-IP permissions; observe failure before implementation.
- [x] Extend the LAN UI check with a real student JOIN/leave/rejoin and IP display before starting a measurement.
- [x] Add own-connection socket metadata to ROOM_STATE, with no new privileges or network scans.
- [x] Wire ROOM_STATE/open/disconnect updates into the LAN workspace.
- [x] Rename/reorder the metric cards as Server and Client, retain the sending direction, add attributed endpoint labels and a live client roster.
- [x] Run full Maven verification, real TLS UI checks and inspect screenshots at 1366×768 and 1024×640.
- [x] Update usage/design documentation and build current JARs.

## Verification — 27/10/2026

- Full Maven verify: 178 tests, zero failures/errors/skipped, all six reactor projects succeeded. Current JARs installed locally after verification.
- The new server endpoint test failed before implementation, then passed with own-socket metadata and unchanged student IP permissions.
- LAN UI test failed before the new endpoint labels existed, then passed with real TLS JOIN, disconnect and a second JOIN. IPs appeared before a measurement; no run was fabricated.
- Existing LAN UI checks also passed: invalid input, live tab switching, stop/reopen, full 30-second loopback run, CSV, disconnect, both viewport sizes and student dialog hosting.
- Rendering fixtures verified two connected identities, disconnect state, correct Server/Client statistic assignment, missing evidence and local-run exclusion. Fixtures are test-only and explicitly labeled.
- Visually inspected the connected-client screen at 1366×768 and saved-run screen at 1024×640. Screenshots are in tmp/lan-peer-ip-ui.
- No physical distance or second physical laptop was claimed. No running user server, database, installer, Git commit or remote service was changed.
