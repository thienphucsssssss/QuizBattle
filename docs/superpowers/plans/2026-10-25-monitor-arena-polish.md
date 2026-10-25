# Monitor and arena polish implementation plan

> **For agentic workers:** Implement the authorized changes task by task in this session; preserve the existing project UI rules.

**Goal:** Improve the two approved management tabs with clearer live progress, actionable filters and readable player results.

**Architecture:** Keep CommanderViews and the existing gateway/navigation. A small presentation model derives filters, counts and permission state from authoritative room snapshots. Reuse native JavaFX tables, bundled avatars/fonts and scoped CSS.

**Tech Stack:** Java 21, JavaFX 21, JUnit 5, existing TLS/SQLite integration QA.

**Spec:** User-approved three-tab proposal (only monitor/arena authorized), AGENTS.md, .cursor/rules/preserve-existing-ui-ux.mdc, design-system/network-quiz-battle/MASTER.md.

## Global constraints

- Preserve navy/cobalt/lime, sidebar, main navigation, student gameplay and room tab.
- No demo data, invented performance metrics, protocol/scoring or role changes.
- Only connected ADMIN/TEACHER room owners can control the match; viewers remain read-only.
- Server ranks, including every tied leader, remain authoritative; search never changes the room roster.
- Fit 1366×768 and 1024×640; tables scroll independently and footer actions remain reachable.

## Review focus

- Disconnected players must remain in the submission denominator, including players who submitted before disconnecting.
- A lost server connection must not label cached connection state as live or leave controls enabled.
- Phase-only updates must refresh cells, filters, progress and player details without navigating away.
- Zero players, no ranking, long names and many tied leaders must fit without fake values or clipping.
- Selection/details must follow player_id across filtering and server refreshes, clearing when the selected player is hidden or removed.

## Task 1: Presentation behavior and permissions

Files: new CommanderModel.java and CommanderModelTest.java.

- [x] Write tests for counts/denominator, phase-aware filters, Vietnamese search, stale states, authoritative ties and control permissions; run and observe missing behavior.
- [x] Implement the small pure presentation model; run the focused tests.

## Task 2: Native management UI

Files: CommanderViews.java, arcade.css, LiveLanUiCheck.java, new CommanderViewsUiCheck.java.

- [x] Add failing live UI assertions for progress, quick filters, player inspection and leader presentation.
- [x] Monitor: progress/timer/question hierarchy, phase-aware quick filters, avatar/name/IP identity cells, status labels, stable footer and disabled-action explanation.
- [x] Arena: compact leader strip, avatar/name table with exact scores/correct counts, existing search/status filter, selected-player details including IP.
- [x] Keep updates local, retain valid selection and filters, use native keyboard/focus behavior and the existing reduced-motion-aware page transitions. No extra ranking animation introduced.
- [x] Run focused Scene QA for compact/normal sizes, long names, ties, role/viewer/disconnected states; inspect saved PNGs.

## Task 3: Verification and notes

- [x] Run Maven reactor install and real TLS/SQLite UI QA at both supported sizes.
- [x] Update the design-system note and document verified behavior/limitations.
- [x] Review the final diff and leave the updated source/build available locally.

## Verification results

- Test-first evidence: the model tests initially reported the absent presentation model; the live QA failed on the old monitor's missing progress bar. A search test then caught accent-sensitive matching before the UI implementation was finalized.
- Full Maven reactor install: 139 tests, zero failures/errors/skips. Final artifacts installed after the last compact-layout adjustment.
- CommanderViewsUiCheck passed at 1366×768 and 1024×640: disconnected submitted players, phase-aware filters, unaccented search, selected identity across updates, clearing hidden selection, restricted IP removal, 12 tied leaders, long names, empty room and reachable footer. Compact arena shows two complete participant rows.
- Final LiveLanUiCheck passed sequentially at both sizes, through real TLS/SQLite authentication, role mismatch rejection, monitor submissions/filters, arena selection, phase changes, imports, question sets and reused history. Snapshots: ../output/monitor-arena-final/{1366,1024}/; component edge cases: ../output/monitor-arena-components-final/.
- One intermediate live run overlapped compilation and failed in the existing import subprocess. A direct worker invocation reproduced ClassNotFoundException while Maven was rewriting target/classes. Final UI runs were scheduled after compilation and both passed; no parser/product behavior changed.
- Native Scene snapshots were visually reviewed; an early leader-label wrap and cramped compact table were corrected. git diff --check passed. Existing JavaFX classpath and POI logging diagnostics are non-blocking. Loopback QA does not replace testing on multiple physical LAN machines.
