# Room Topology Implementation Plan

> Implement sequentially in this chat with the existing TDD workflow. No subagents. The requested automatic metre measurement is hardware-blocked; do not replace it with manual or estimated values.

**Goal:** Add a room-centred diagram of actual joined clients, using the supplied image as composition reference while preserving QuizBattle's existing UI.

**Architecture:** Read the existing authoritative ROOM_STATE. A pure presentation model derives connection/readiness labels, validates genuine hardware distance evidence if a provider supplies it, and computes responsive orbit positions. A JavaFX component draws lines/room/avatar nodes and opens real participant details. No synthetic participants or distances.

**Tech Stack:** Java 21, JavaFX 21, Jackson, existing FxKit/QuizlingArt/CSS, JUnit.

**Spec:** User's reference image and follow-up choices on 25/10/2026: automatic Wi-Fi RTT; currently only one Windows laptop. Read-only inspection: MediaTek MT7921, driver 3.0.1.1325 reports FTM as Initiator: Not Supported.

## Constraints

- Preserve room create/configure/join/ready/start/reset/close, question source, sidebar and colours.
- Centre contains the actual room code; nodes come only from players[] and retain identity by player_id.
- Node coordinates illustrate topology, not measured positions or distance scale.
- Missing distance reads “Chưa có dữ liệu RTT”. Never reuse manual LAN-range distance, derive metres from ping/RSSI, or create random values.
- A valid future hardware measurement must explicitly identify WIFI_RTT, SERVER reference, timestamp and a finite non-negative numeric distance. AP-referenced distance must be labelled as distance to AP, never room/server distance.
- The present Windows client has no hardware ranging provider. The UI cannot enable real automatic metres until compatible hardware/API integration exists.
- Disconnected controller renders cached participant state as unconfirmed, never live.
- No credentials/database/installed release changes.

## Files

- Create admin/RoomTopologyModel.java, RoomTopologyView.java and corresponding JUnit/UI checks.
- Modify ArcadeApplication.lobby(), disconnect handling and scoped arcade.css.
- Add max_players/server_port to server room snapshots; extend an existing real TLS test.
- Update room/UI docs with actual hardware limitation and the meaning of the graph.

## Execution

- [x] Add failing model tests: real roster, readiness, missing/invalid/manual/AP distances, stale data and collision-free layout for 1–10 nodes.
- [x] Implement the model with one clock boundary for distance freshness; derive display values only from received evidence.
- [x] Add a failing real JavaFX/TLS room check, then replace lobby avatar tiles with the graph.
- [x] Keep existing room controls reachable at 1366×768 and 1024×640; inspect screenshots for populated/empty/long-name/disconnected states.
- [x] Run targeted tests, full Maven verify and the existing real UI regression; record results.
- [ ] Automatic hardware ranging: blocked by the present driver/hardware. Leave unavailable and document what remains.

## Verification evidence

- Model tests first failed for the missing class, then 4 passed.
- Real server test first failed because server_port was absent (0 instead of actual listener port), then passed with actual port/capacity/IP and no fabricated metre fields.
- UI test first failed because the topology component was absent, then ROOM_TOPOLOGY_UI_PASS with real TLS empty/JOIN/READY/details/disconnect. Multi-client screenshots are explicitly labelled fixture.
- Student-theme contrast regression first failed (light text on white), then passed after scoped CSS fixes. Final screenshots inspected for both themes, centre readability, long names, 8/10 nodes and both window sizes.
- Full Maven verify: 172 tests; failures=0, errors=0, skipped=0; all six reactor projects BUILD SUCCESS.
- Source JARs installed after verification so opt-in UI tests use current code/styles. Existing LiveLanUiCheck reports COMMANDER_UI_PASS, QUESTION_IMPORT_UI_PASS, QUESTION_SET_UI_PASS, SERVER_COMPATIBILITY_UI_PASS and LIVE_LAN_UI_PASS.
- Logs/images: tmp/room-topology-verify.log, tmp/room-topology-ui-final.log, tmp/room-topology-ui/, tmp/room-topology-live-regression.log and tmp/room-topology-regression/.
- No physical metre measurement was performed. The Windows client has no hardware ranging provider, and its current MT7921 driver reports FTM as Initiator: Not Supported. Completing actual automatic ranging requires compatible hardware plus the API/provider integration.
- Source/docs/JAR build only; no installed release, database, certificate, driver or firewall change. Existing unrelated launcher work preserved.
