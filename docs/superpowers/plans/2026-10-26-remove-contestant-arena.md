# Remove contestant arena implementation plan

**Goal:** Remove the “Đấu trường thí sinh” management tab completely, as requested by the user.

**Architecture:** Remove its sidebar entry, page handler, dedicated presentation/model logic and styles. Keep monitoring, room management, gameplay ranking and saved results working through their existing routes.

**Tech Stack:** Java 21, JavaFX 21, Maven, JUnit 5.

**Spec:** User request in this chat; `AGENTS.md` and `.cursor/rules/preserve-existing-ui-ux.mdc`.

## Constraints and review focus

- Preserve existing unrelated working tree edits.
- Admin and teacher navigation must omit the removed page; remaining actions must still work.
- Monitor filters, player IPs, phase updates and disconnections must keep working.
- Do not remove gameplay ranking, stored results or server data needed by other screens.

## Implementation

- [x] Update `CommanderViewsUiCheck` to check navigation without the arena for Admin and teacher, and reject the old route. Run against the existing code and observe the expected failure.
- [x] Remove the arena entry from `ManagementSidebar`, its screen and helpers from `CommanderViews`, arena-only sorting/search/leader helpers from `CommanderModel`, and unused styles from `arcade.css`.
- [x] Update `CommanderModelTest`, `CommanderViewsUiCheck` and `LiveLanUiCheck` to cover the remaining monitor behavior and stop invoking the removed page.
- [x] Update current navigation/IP documentation and the design system; retain historical plans as records.
- [x] Run Maven install for the full reactor, component UI checks at 1366×768 and 1024×640, and live TLS UI QA. Review the diff and record results.

## Verification

- Red: component UI check failed with `ADMIN still exposes the removed contestant arena` before production changes.
- `mvn -B -ntp install`: BUILD SUCCESS; 171 tests, zero failures/errors/skips.
- `CommanderViewsUiCheck`: PASS at 1366×768 and 1024×640; remaining navigation, Admin/teacher visibility, removed route rejection, monitor filters, phase updates and disconnection controls verified.
- `LiveLanUiCheck`: PASS at both sizes with real TLS/SQLite, including monitoring, gameplay, stored ranking, imports, question sets and account roles.
- Reviewed snapshots in `../output/remove-arena-components/` and `../output/remove-arena-live/{1366,1024}/`.
- `git diff --check`: clean. No arena references remain in production Java or CSS.
