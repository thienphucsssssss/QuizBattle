# QuizBattle UI System Refresh Implementation Plan

> Execution: implement in this session, following the user's approved proposal. Review the rendered JavaFX output after each functional group.

**Goal:** Upgrade all existing desktop UI surfaces with consistent semantic colors, light/dark appearance, readable layouts and preserved LAN behavior.

**Architecture:** Keep JavaFX native controls, existing views, gateway and models. A presentation-only `UiTheme` applies appearance to scene roots and dialogs without rebuilding their content. Semantic looked-up CSS colors cover the existing component families; game surfaces explicitly retain their arcade palette.

**Tech Stack:** Java 21, JavaFX 21.0.12, Maven, existing JavaFX/TLS QA entry points.

**Spec:** `docs/De_xuat_UI_UX_2026-10-29.md`, approved by the user on 29/10/2026.

## Global Constraints

- Preserve sidebar navigation, logo, Be Vietnam Pro, mascots, game timing and existing interaction flow.
- No changes to protocol, scoring, authorization, Server persistence or production data.
- Desktop targets: 1024×640, 1366×768, 1920×1080; offline resources only.
- Theme must cover dialogs and popup controls, persist locally for ordinary sessions and leave user preferences untouched in QA.
- Preserve reduced motion and existing loading/empty/error/success behavior.

## Review Focus

- Switching appearance while editing must preserve field objects and values.
- Newly opened and already visible dialogs must follow appearance; popup menus must remain legible.
- Dark appearance must not turn text white on pastel answers, medal faces or lime actions.
- Narrow windows and long Vietnamese text must retain reachable actions and scrolling.
- Live tests must keep test data isolated and preserve authoritative Server ACK/results.

## Task 1: Theme and entry surfaces

Files: new `UiTheme.java`; `FxKit.java`, `ArcadeApplication.java`, `arcade.css`; new `UiThemeUiCheck.java` on test classpath.

- [x] Add and run a failing UI check for the missing appearance selector.
- [x] Implement `UiTheme.apply(Parent)` and `UiTheme.setDark(boolean)`; expose Sáng/Tối through existing options, persist only outside QA.
- [x] Theme scene roots, existing/new dialogs and popup controls without reconstructing forms.
- [x] Polish auth layout, focus, form labels, feedback and shared controls using semantic tokens.
- [x] Run theme UI check for field retention, dialog propagation, contrast and QA preference isolation.

## Task 2: Authoring and room operation

Files: `ManagementViews.java`, `ManagementSidebar.java`, `CommanderViews.java`, `arcade.css`.

- [x] Reduce wasted bank row/toolbar space while preserving preview readability and checkbox targets.
- [x] Apply a common heading/control/status hierarchy to bank, import, room, monitoring and account pages.
- [x] Ensure sidebar and footer remain usable at minimum desktop size.
- [x] Run existing component checks and the real TLS UI flow at 1366×768; inspect screenshots.

## Task 3: Gameplay and results

Files: `ArcadeApplication.java`, `RankBoard.java`, `HistoryResults.java`, `VictoryPodium.java` as needed; `arcade.css`.

- [x] Pin game foreground/pastel answer colors independently of application theme.
- [x] Check long questions, ACK feedback, ranking, podium/medals, history and projector; preserve the same arcade palette in both themes.
- [x] Preserve deadline, answer transitions, rank and tie handling; fix presentation only.
- [x] Run the real TLS UI flow at 1024×640 and focused podium/history UI checks.

## Task 4: Tools, verification and documentation

Files: `LanRangeView.java` as needed; `arcade.css`; design system and QA documentation.

- [x] Cover LAN range tools, settings, users and remaining dialogs with semantic states.
- [x] Run focused LAN UI checks and Maven verify/install; inspect wide/narrow light/dark captures.
- [x] Review the source diff for unrelated changes and CSS/runtime errors.
- [x] Update the existing design system with final tokens, theme ownership, component conventions and evidence/limits.

Evidence and limitations: `docs/UI_UX_2026-10-29.md`.

No test will merely assert CSS literals. Behavioral theme tests and existing integration tests verify meaningful outcomes; visual changes are checked in rendered JavaFX scenes.
