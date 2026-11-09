# V3 Knowledge / RAG — Acceptance Record

- **Updated:** 2026-11-09 (Australia/Sydney)
- **Status:** `PARTIAL — core live path passes; retrieval negative cases, browser QA, and current-head CI remain`
- **Base:** `f1e38a0b339f763368a63d2c6a5a297f0f8f11ee` (merge of PR #9 on `origin/main`)
- **Branch:** `feat/v3-knowledge-rag`
- **V3 PR / current-head CI:** branch pushed at `dae7331`; PR creation blocked by GitHub integration 403; no CI runs/statuses are available yet

This record distinguishes automated checks from end-to-end service acceptance. The isolated E5/Qdrant/Ollama/Spring/HTTP path has now been exercised locally. V3 remains incomplete: the retrieval evaluation returns irrelevant matches for all three negative queries, browser acceptance has not been run, and current-head CI has not run.

## Verification observed locally

| Area | Result | Evidence |
|---|---|---|
| Python lock/lint/tests | **PASS** | `uv lock --check`; Ruff passed; 17 pytest tests passed. The embedded-Qdrant test warns that payload indexes are inactive in local mode; pytest also warned its cache could not be written. CI uses server Qdrant for its vector-store test. |
| Frontend lint/tests/build | **PASS** | Node 24.19.0 / npm 11.3.0; lint passed, 32 tests in 5 files passed, and the Vite production build passed. Tests include workspace-switch cancellation and no replay of an old question into a newly selected workspace. |
| Backend unit tests/package | **PASS** | `mvn clean test`: 54 tests, no failures/errors/skips; `mvn package` passed. Compiled with Java release target 21; Maven itself ran on local JDK 26.0.2. |
| Backend Testcontainers integration | **BLOCKED LOCALLY** | `mvn clean verify` passed unit tests, then failed at `V2ProductionBackendIT`: Testcontainers received HTTP 400 with empty Docker daemon metadata. This is a local Docker Desktop/Testcontainers environment failure, not a failing assertion. CI remains pending. |
| Pushed branch / PR / CI | **PARTIAL** | `feat/v3-knowledge-rag` is pushed at `dae7331`. GitHub PR creation returned `403 Resource not accessible by integration`; local `gh auth status` reports its token is invalid. GitHub status and workflow-run queries for this SHA returned no checks/runs. |
| Isolated V3 stack / migrations | **PASS** | MySQL 8.4, Redis, Qdrant, FastAPI, Ollama, and backend started with unique V3 container/volume names. Flyway V1–V8 and Hibernate schema validation passed; `GET /api/health` returned `200 {"status":"UP"}`. The original MySQL container/volume were not used. |
| Index lifecycle / retrieval | **PASS** | A real document moved from MySQL outbox `PENDING` to `READY`; pinned E5 weights loaded and Qdrant returned a workspace-scoped passage. Editing verified only the current revision could be returned; archive tombstones removed the document from search. Non-member access returned 401. |
| Grounded answers / model outage | **PASS** | Warm and cold Ask flows returned a grounded answer with a server-verified current citation; an unsupported question returned `INSUFFICIENT_EVIDENCE` with no sources. During an Ollama-only outage, passage search still succeeded and Ask returned sanitized 503; Ask recovered after restart. The measured cold Ask completed in 137.8 seconds with the 300-second timeout. |
| CORS | **PASS** | Browser-origin preflight from `http://localhost:5173` to the backend returned 200 with the expected origin, methods, and headers. This is an API preflight check, not browser acceptance. |
| E5/Qdrant retrieval evaluation | **PARTIAL / FAILS NEGATIVE CASES** | Real run recorded in [`docs/evaluation/v3-local-2026-11-09.json`](evaluation/v3-local-2026-11-09.json): 7 synthetic documents / 8 queries; Recall@5 1.0, Hit@5 1.0, MRR@5 1.0 for positive queries, but negative queries empty `0/3`. Maximum negative score is 0.7622 while one positive paraphrase scores 0.741. Do not tune the score threshold on this tiny corpus alone. |
| Browser UI acceptance | **NOT RUN** | No V3 browser interaction was performed in this session. Frontend unit tests/build do not establish browser behavior. |
| Frozen V1 contract/migrations | **PASS** | `docs/API_CONTRACT.md` and migrations V1–V7 have no diff. Only V8 is added. |
| Local credentials | **PASS / ROTATED** | A generated local internal token was rotated after it appeared in diagnostic output. `.env.v3` is ignored and not tracked; no credential value is recorded here. |
| `git diff --check` | **PASS** | Re-run after any remaining PR/CI status edits and before commit. |

## Remaining live acceptance gates

Run remaining checks only with the isolated `docker-compose.v3.yml` resources. Preserve the original `opspilot-mysql` container and `opspilot_mysql_data` volume.

- [ ] Improve negative-query behavior using an expanded and labeled corpus, then rerun the E5/Qdrant evaluation. Keep positive recall in view; do not choose a threshold solely to fit the current 8 queries.
- [ ] In a browser, verify safe text rendering, citation navigation, loading/error/retry states, owner-only reindex control, and no response/cache carryover on user/workspace switch.
- [ ] Exercise prompt-injection text in a synthetic document through the live Ask flow; verify the model treats it as untrusted evidence.
- [ ] Verify retry/backoff reaches bounded `FAILED`; OWNER/ADMIN reindex returns `202`, MEMBER receives `403`, a workspace over 500 active docs gets `413`, and cooldown returns `429` plus `Retry-After`.
- [ ] Run current-head backend, AI service, and frontend CI on the pushed PR branch. The local Testcontainers failure must be distinguished from CI results.
- [ ] Create a draft PR from `feat/v3-knowledge-rag` to `main` after GitHub PR write access is restored, then record its current-head CI result here.

## Scope and data boundary

- MySQL is the source of truth; Qdrant data is derived and rebuildable.
- Spring owns authentication, authorization, and current-document/revision checks. The Python service is private and accepts only internal-token requests.
- The app uses local E5 embeddings and Ollama generation. No paid AI API or cloud inference is configured.
- V1 API contract and migrations V1–V7 are frozen. V4 Agent work has not started.
