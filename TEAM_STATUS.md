# OpsPilot — Team Status

> **Purpose:** lightweight shared task handoff log.
> Update this file when an assigned task is completed, blocked, handed off, or materially changes scope.
> Do not paste private Codex prompts here.

## Current sprint

| Member | Track | Current task | Status | Branch / PR | Verification | Handoff / Blocker | Updated |
|---|---|---|---|---|---|---|---|
| Joan | Backend / Architecture | Phase 8 — Activity Logs + Dashboard | Done | `main` / Phase 8 verified | Live MySQL/Flyway V7; JWT/HTTP; activity feed; dashboard; overdue behavior; tenant isolation; `mvn clean test` PASS (36 tests); `mvn package` PASS | V1 Backend Freeze / frontend API contract review | 2026-08-25 |
| Member A | Frontend / Product | V1 frontend foundation | Ready | — | — | Read roadmap/API and begin scoped frontend branch | 2026-08-25 |
| Member B | QA / DevOps | Fresh-clone / smoke-test / CI preparation | Ready | — | — | Validate onboarding and propose scoped CI/smoke checks | 2026-08-25 |

## Update rule

When you finish or hand off a task, update **your row** above.

Record only:

- **Current task:** short human-readable scope.
- **Status:** `Ready`, `In progress`, `Blocked`, `Review`, or `Done`.
- **Branch / PR:** branch name and PR number/link when available.
- **Verification:** tests/build/integration checks actually run.
- **Handoff / Blocker:** what the next person needs to know.
- **Updated:** `YYYY-MM-DD`.

If you start a new task after one is merged, replace your current row with the new active task. Important completed milestones belong in `PROJECT_ROADMAP.md`; detailed history already exists in Git commits/PRs and should not be duplicated here.

## Example

| Member | Track | Current task | Status | Branch / PR | Verification | Handoff / Blocker | Updated |
|---|---|---|---|---|---|---|---|
| Member A | Frontend | Login/Register UI | Review | `feat/frontend-auth` / PR #12 | `npm test`, `npm run build` | Waiting for Joan API review | 2026-08-27 |
