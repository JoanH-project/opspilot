# OpsPilot — Team Status

> **Purpose:** lightweight shared task handoff log.
> Update this file when an assigned task is completed, blocked, handed off, or materially changes scope.
> Do not paste private Codex prompts here.

## Current sprint

| Member | Track | Current task | Status | Branch / PR | Verification | Handoff / Blocker | Updated |
|---|---|---|---|---|---|---|---|
| Joan | Owner / Full-stack / AI Integration | V3 final acceptance — live HTTP boundary checks | Review | `feat/v3-knowledge-rag` / [PR #10](https://github.com/JoanH-project/opspilot/pull/10) open | Acceptance code SHA `b8adb8903724f918ecb518da8114fe38a0c4a2b2`: Backend CI (Java 21, MySQL 8.4 + Redis Testcontainers, Flyway V1–V8, real HTTP) 3 boundary tests PASS; [JUnit artifact](https://github.com/JoanH-project/opspilot/actions/runs/43378130105/artifacts/14015283475). [Backend](https://github.com/JoanH-project/opspilot/actions/runs/43378130105), [AI Service](https://github.com/JoanH-project/opspilot/actions/runs/43378130078), and [Frontend](https://github.com/JoanH-project/opspilot/actions/runs/43378129996) workflows all PASS. Local `mvn -DskipTests test-compile` PASS; `mvn test` 55 tests PASS. | MEMBER 403, repeated 501-document 413 without limiter/task writes, 8 actual Worker failures to persisted terminal FAILED (`next_attempt_at`/lease NULL), no ninth retry, and recovery to READY all PASS in CI. Only AI HTTP dependency was stubbed on localhost; test Clock accelerated the unchanged retry schedule. Local Docker Desktop still does not respond; local `mvn clean verify` was not run or claimed. V3 is `V3_READY_FOR_HUMAN_REVIEW`; PR remains open, no merge or V4. Preserve V1 API, V1–V7 migrations, V8 boundary, and local E5/Ollama approach. See acceptance docs. | 2026-11-13 |
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
