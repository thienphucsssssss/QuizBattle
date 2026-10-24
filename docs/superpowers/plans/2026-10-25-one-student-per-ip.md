# One Active Student per IP Implementation Plan

> **For agentic workers:** Implement task-by-task in the current session; write and run failing integration tests before the server change.

**Goal:** Enforce one active student per socket source IP, regardless of nickname or application instance.

**Architecture:** Validate JOIN on the existing single game worker before allocating a player or avatar. Compare authoritative socket IPs against connected students; retained disconnected match entries do not reserve an IP. Keep manager authentication and the existing disconnect/room lifecycle.

**Tech Stack:** Java 21, TLS sockets, SQLite, Maven, JUnit 5.

**Spec:** User request on 25/10/2026: “Mỗi máy/IP chỉ được có một học sinh hoạt động tại một thời điểm. Mở thêm ứng dụng hoặc dùng tài khoản học sinh khác trên cùng IP phải bị từ chối.”

## Global Constraints

- Preserve existing uncommitted work and current UI.
- Trust socket source IP only; ignore client-supplied IP/identity fields.
- No protocol version or database schema change; enforce at JOIN, including legacy clients.
- Active membership lasts until LEAVE/LOGOUT, detected disconnect, or room close; reset keeps connected students reserved.
- Managers can share an IP with a student. Same-IP devices behind NAT share the limit; IP alone cannot identify one machine across different addresses.

## Review Focus

- Different nicknames and forged IP fields cannot bypass the guard.
- Simultaneous JOIN requests on one IP have exactly one winner.
- Rejected JOIN neither gains student permissions nor displaces the existing student.
- LEAVE/LOGOUT and detected disconnect permit a replacement; reset does not free a connected student's IP.
- Distinct source IPs still support multiple students and existing game/ranking tests.

### Task 1: JOIN enforcement and lifecycle verification

**Files:** Modify `nqb-server/src/main/java/vn/edu/hutech/nqb/server/QuizServer.java`; extend `nqb-server/src/test/java/vn/edu/hutech/nqb/server/ServerIntegrationTest.java` and the opt-in `nqb-admin-client/src/test/java/vn/edu/hutech/nqb/admin/LiveLanUiCheck.java` network fixtures.

**Interfaces:** JOIN returns existing ERROR envelope with `code=IP_ALREADY_IN_USE` and a Vietnamese explanation when another student is active at the same source IP. Successful JOIN and all existing lifecycle messages retain their schemas.

- [x] Add real TLS tests for duplicate/spoofed JOIN, concurrent JOIN, distinct IPs, and IP release/retention over leave, logout, disconnect, reset and close. Bind test forwarding sockets to separate loopback source IPs to model distinct student machines without changing production networking.
- [x] Run targeted Maven tests; verify duplicate JOIN succeeds before the fix and makes the rejection tests fail.
- [x] Add the guard on the game worker before player/avatar allocation.
- [x] Run the targeted tests and full `mvn -B -ntp install` (includes verify); update opt-in UI fixtures to honor separate student IPs.
- [x] Document the error, active-session lifecycle, NAT/IP limits, verification result and source-build release scope.

### Documentation

**Files:** Update `docs/Giao_thuc_Java_LAN.md` and `docs/Theo_doi_hoc_sinh_va_IP.md` with the authoritative rule and deployment instructions.

## Verification results

- Seven new TLS integration cases failed before the guard (the server accepted duplicate same-IP students), then all seven passed after the guard.
- All 33 server integration cases pass, including ten students using distinct real loopback source addresses through transparent TCP forwarding. The production gateway still verifies TLS.
- Full `mvn -B -ntp install`: all six reactor projects succeed; 126 tests, zero failures/errors/skips. Built source JARs are installed in the local Maven repository.
- Full JavaFX/TLS UI QA passes at 1024×640. It verifies visible same-IP rejection, unchanged active membership, successful retry after LEAVE, three-player gameplay, management monitoring, imports, question sets and old-server guidance.
- The first full UI run exposed an existing race in `checkUnsupportedQuestionSets`: ROOM_STATE could rebuild the lobby between button lookup and click. The test now lays out, finds and clicks the button in one FX turn; the full rerun passes.
- Inspected screenshots of the duplicate-IP message and management table with distinct source IPs. Final UI output: `tmp/one-student-ip-ui-final/`; log: `tmp/one-student-ip-ui-final.log`. Initial red/green logs and the full build log are in `tmp/one-student-ip-*.log`.
- Existing JavaFX unnamed-module and POI logging-provider diagnostics remain in opt-in UI QA; the final run succeeds.
- No production Server restart, installer replacement, database change or physical multi-machine LAN acceptance was performed. Enforcement is based on observed source IP, not hardware identity.
