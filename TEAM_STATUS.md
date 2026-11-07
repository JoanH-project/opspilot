# OpsPilot — Team Status

> **Purpose:** lightweight shared task handoff log.
> Update this file when an assigned task is completed, blocked, handed off, or materially changes scope.
> Do not paste private Codex prompts here.

## Current sprint

| Member | Track | Current task | Status | Branch / PR | Verification | Handoff / Blocker | Updated |
|---|---|---|---|---|---|---|---|
| Joan | Owner / Full-stack | V2 Production Backend — final CI fixes | Review | `feat/v2-production-backend` / [PR #9](https://github.com/JoanH-project/opspilot/pull/9) @ `24e4810` | Current-head GitHub CI PASS: 48 unit tests, 4 `V2ProductionBackendIT` tests, Flyway V1–V7, Java 21 image build/runtime; frontend install/tests/lint/build PASS. Local isolated-copy `mvn test`: 48 PASS. `git diff --check` PASS. | Ready for PR review; PR #9 is open and not merged. Prometheus exporter was explicitly enabled after CI showed `management.defaults.metrics.export.enabled=false` prevented its endpoint from registering. Host Testcontainers still cannot use this Docker Desktop API; GitHub Docker-backed verification passes. Preserve V1 contract. `URGENT` enum decision and 10 moderate dev-only npm advisories remain follow-ups. | 2026-11-08 |
| Member A | Frontend / Product | Frontend F2 — Workspace Bootstrap + Real Dashboard | Done | `main` / PR #6 merged | `npm ci`; `npm test` (13 passed); `npm run lint`; `npm run build`; `git diff --check` all pass | F2 is merged. Later F2 hardening and F3/F4 work is included in PR #7; no F2 review remains. | 2026-11-06 |
| Member B | QA / DevOps | Q3 — Auth Regression Checklist (R4 browser verification) | In progress | `main` / PR #5 merged (checklist) | `docs/AUTH_REGRESSION.md` merged. Human F1 acceptance reported R1 duplicate 409, R2 wrong-password 401, and R3 network failure/recovery PASS. R4 has not been verified. | Follow up with the documented login-success/`/api/users/me`-failure browser check; do not report R4 as PASS without observing it. This is separate from the merged V1 milestone. | 2026-11-06 |

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
