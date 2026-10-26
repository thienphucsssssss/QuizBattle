# Build-once Windows Launcher Implementation Plan

> **For agentic workers:** Implement the authorized launcher change in this session, task by task. No delegation is needed.

**Goal:** Prepare dependencies and compiled artifacts once, then reuse them when opening Server or Client until the user explicitly chooses Build / update.

**Architecture:** Keep the existing batch menu and offline Maven launchers. Replace source fingerprint invalidation with a completed-build marker plus checks that each module still has its JAR and runtime classes. Accept the existing JSON build marker so upgrading this launcher does not itself force a build.

**Tech Stack:** Windows batch, Windows PowerShell, Maven, JUnit 5.

**Spec:** User request in this chat on 2026-10-25: build and download dependencies on first use; later launches must reuse that preparation.

## Global Constraints

- Preserve Java 21, Maven discovery and existing offline runtime commands.
- Keep explicit `run.bat build` and menu option 1 as the way to apply source changes.
- Never mark a failed Maven build or launcher-plugin preparation as ready.
- Leave existing configuration, certificates, accounts, database and unrelated work untouched.

## Review Focus

- Source, POM or script changes must not automatically rebuild a completed installation.
- Missing runtime outputs must trigger preparation again.
- Failed preparation must return failure and remain retryable.
- Existing valid JSON markers must remain reusable; invalid markers must not skip preparation.
- Explicit Build must still replace old artifacts even when preparation was already complete.

### Task 1: Implement and document build-once launch behavior

**Files:**
- Modify: `nqb-server/src/test/java/vn/edu/hutech/nqb/server/SourceLauncherTest.java` — execute the real batch and PowerShell scripts in a temporary project, substituting only Java/Maven executables.
- Modify: `scripts/Source-Build.ps1` — validate readiness without reading source inputs.
- Modify: `run.bat` — clarify first preparation, reuse and manual update in menu/help.
- Modify: `README.md`, `docs/PHAT_TRIEN_NHOM.md`, `docs/Bo_cau_hoi_va_phong_thi.md` — describe the current launcher behavior.

**Interfaces:** Existing batch commands and `Source-Build.ps1 [-Record]` remain compatible. Exit code 0 means usable preparation; 1 means preparation is needed or failed.

- [x] Update launcher tests for unchanged/source-edited/deleted projects, changed or missing runtime outputs, valid old markers, failed build/plugin preparation and manual updates.
- [x] Run the launcher tests and confirm the source-change reuse case fails against current production scripts (4 expected failures before implementation).
- [x] Implement marker/output validation and update batch messages and user documentation (9 launcher tests pass).
- [x] Run launcher tests, then the full offline Maven test suite; check diffs and `run.bat help`.

**Verification:** Full `mvn -o -B -ntp install` succeeded for all five modules. Offline `exec:help` succeeded; the completed-build marker was recorded and the readiness check returned 0. Logs: `tmp/launcher-build-once-verify.log`, `tmp/launcher-build-once-plugin.log`.
