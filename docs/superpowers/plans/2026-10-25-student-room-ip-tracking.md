# Student Room/IP Tracking Implementation Plan

> **For agentic workers:** Implement task-by-task in the current session with a failing integration test before each behavior change.

**Goal:** Let the server track each student's current room and socket IP, and show the IP in the existing management views.

**Architecture:** Keep the authoritative IP on the in-memory Player created by a successful TLS JOIN. Include it in room snapshots only for the room owner/Admin, and emit structured console logs for membership and room changes from the game worker.

**Tech Stack:** Java 21, TLS sockets, Jackson, Java logging, JavaFX 21, Maven/JUnit 5.

**Spec:** User request on 25/10/2026: “server tracking được học sinh ở phòng nào và IP là gì”.

## Global Constraints

- Preserve the current JavaFX UI and existing uncommitted work.
- Derive IP from the socket; never trust IP or player identity supplied by the client.
- Keep protocol version 1, existing database schema, and one active room per server.
- Do not expose IPs to students, unrelated teachers, public discovery, rankings, or saved match results.

## Review Focus

- A forged IP in JOIN must not change the recorded address.
- Students and unrelated teachers must not receive IP fields in responses/events.
- A disconnected participant retains their last IP during the game, with connected=false.
- Reset/rename/close must log the correct room and avoid duplicate leave events.
- Existing management tables must remain usable at 1366×768 and narrower widths.

### Task 1: Authoritative room snapshots and lifecycle logs

**Files:** Modify `nqb-server/src/main/java/vn/edu/hutech/nqb/server/QuizServer.java`; test `nqb-server/src/test/java/vn/edu/hutech/nqb/server/ServerIntegrationTest.java`.

**Interfaces:** `ROOM_STATE.players[].ip_address` for owner/Admin; `NQB_STUDENT_TRACKING` JSON log records with timestamp, event, player ID/nickname, room code/name, IP, and connected state.

- [x] Write real TLS tests for spoofed addresses, access control, disconnect retention, reset cleanup, and lifecycle logs.
- [x] Run the targeted Maven tests and confirm failure due to missing tracking.
- [x] Capture the socket IP at JOIN; enrich authorized snapshots and log JOINED, LEFT, DISCONNECTED, ROOM_UPDATED, ROOM_RESET, ROOM_CLOSED.
- [x] Run targeted tests and confirm they pass.

### Task 2: Existing management tables

**Files:** Modify `nqb-admin-client/src/main/java/vn/edu/hutech/nqb/admin/CommanderViews.java` and extend the existing live UI check without replacing its pending edits.

**Interfaces:** Consume `players[].ip_address` and existing room header; show a dash when the server does not supply the field.

- [x] Add live UI assertions for visible IP values in monitor/arena tables; disconnect retention is covered by the server integration test.
- [x] Add an IP column to the two tables using current styles and sizing.
- [x] Run the real JavaFX/TLS UI check and inspect the resulting screenshots.

### Task 3: Documentation and full verification

**Files:** Update `docs/Giao_thuc_Java_LAN.md`; create `docs/Theo_doi_hoc_sinh_va_IP.md`.

- [x] Document how to view students' room/IP and read structured server logs, with LAN/loopback and single-room limits.
- [x] Run `mvn -B -ntp verify`, review the final diff, and report test results and release scope.

## Verification results

- Targeted server tests: 3 expected failures before implementation; 3 passes after implementation.
- Live UI: expected failure for missing visible IP before the column change; complete checks pass at 1366×768 and 1024×640 afterward.
- Full Maven verify: 119 tests, zero failures/errors/skips; all six reactor projects succeed.
- Screenshots inspected: monitor and arena at both sizes in `tmp/student-tracking-ui/` and `tmp/student-tracking-ui-1024/`.
- Existing JavaFX unnamed-module and POI logging-provider diagnostics are still printed by live UI QA; neither run failed.
- Built/installed source JARs only. No deployed Server restart, installer replacement, database change, or physical multi-machine LAN acceptance was performed.
