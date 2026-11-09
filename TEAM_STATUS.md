# OpsPilot — Team Status

> **Purpose:** lightweight shared task handoff log.
> Update this file when an assigned task is completed, blocked, handed off, or materially changes scope.
> Do not paste private Codex prompts here.

## Current sprint

| Member | Track | Current task | Status | Branch / PR | Verification | Handoff / Blocker | Updated |
|---|---|---|---|---|---|---|---|
| Joan | Owner / Full-stack / AI Integration | V3 Knowledge / RAG final acceptance and fixes | In progress | `feat/v3-knowledge-rag` / [PR #10](https://github.com/JoanH-project/opspilot/pull/10) open | AI service lock/Ruff/18 tests PASS; frontend lint/32 tests/build PASS; backend `mvn clean package` 55 tests PASS. Isolated V1–V8, browser Ask/search, reindex 202/429 with readable Retry-After, and E5 evaluation PASS. Previous PR head Frontend/AI CI passed; Backend CI failed on nested-ID parsing and has a regression fix pending new-head CI. | Keep PR open. Latest-head CI, live MEMBER 403/413/retry-exhaustion checks, and local Testcontainers remain outstanding. Preserve V1 contract and V1–V7 migrations; do not merge or start V4. See `docs/V3_ACCEPTANCE.md`. | 2026-11-09 |
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
