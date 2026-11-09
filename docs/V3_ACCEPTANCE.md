# V3 Knowledge / RAG — Acceptance Record

- **Updated:** 2026-11-09 (Australia/Sydney)
- **Status:** `PARTIAL — core local, browser, and retrieval flows pass; current-head CI and several live boundary checks remain`
- **Base:** `f1e38a0b339f763368a63d2c6a5a297f0f8f11ee` (PR #9 merge on `origin/main`)
- **Branch:** `feat/v3-knowledge-rag`
- **PR:** [#10](https://github.com/JoanH-project/opspilot/pull/10), open to `main`, not merged
- **Latest fully verified code head:** `01bc9f6`; all three workflows passed. Backend CI ran the MySQL/Redis Testcontainers integration and built the Java 21 image. The acceptance-test helper now handles both object and array responses, with regression assertions for nested IDs.

This record separates local and browser observations from CI results. V3 remains in progress. Do not merge or start V4 until the remaining checks and review gates are resolved.

## Verification observed locally

| Area | Result | Evidence |
|---|---|---|
| AI service lock, lint, and tests | **PASS** | `uv lock --check`; Ruff passed; 18 pytest tests passed. Embedded Qdrant warns that payload indexes are inactive in local mode; the server-Qdrant test remains covered by CI. |
| Frontend lint, tests, and build | **PASS** | The unchanged frontend passed lint, 32 tests in 5 files, and Vite production build. No frontend source was changed during this follow-up. |
| Backend unit tests and package | **PASS** | `mvn clean package`: 55 tests, no failures/errors/skips; Java compiler release target 21. Docker rebuilt the isolated backend image successfully from its Java 21 build image. |
| Backend Testcontainers integration | **BLOCKED LOCALLY** | `mvn clean verify` passes unit tests but Testcontainers cannot validate this Docker Desktop daemon: the selected Docker socket returns HTTP 400 with empty daemon metadata. `docker compose` itself works. The explicit `DOCKER_HOST` retry produced the same local Testcontainers failure. |
| PR #10 code CI | **PASS** | At head `01bc9f6`, [Backend CI](https://github.com/JoanH-project/opspilot/actions/runs/42602327888), [AI Service CI](https://github.com/JoanH-project/opspilot/actions/runs/42602327877), and [Frontend CI](https://github.com/JoanH-project/opspilot/actions/runs/42602327932) all passed. Backend CI includes Testcontainers integration and Java 21 image/runtime verification. Earlier failed heads were corrected by making the acceptance-test helper read top-level IDs from object and array responses. |
| Isolated V3 stack and migrations | **PASS** | Isolated MySQL 8.4, Redis, Qdrant, Ollama, AI service, and Spring backend ran together. Flyway V1–V8 and Hibernate validation passed; `GET /api/health` returned `200 {"status":"UP"}`. The pre-existing MySQL container and volume were not used. |
| Browser registration and workspace flow | **PASS** | Automated browser session registered/logged in, created a workspace and document, indexed it, and used workspace-scoped search. This is agent-controlled browser evidence, not human acceptance. |
| Grounded Ask and safety | **PASS, with latency caveat** | Ask returned the current runbook answer and citation while ignoring an instruction embedded in the document. An unsupported private-address question returned `NOT ENOUGH EVIDENCE` without sources. Under concurrent model/evaluation load one Ask returned a sanitized 503; retry after the stack was idle succeeded in 32.1 seconds. |
| Reindex and cooldown | **PASS** | Browser-origin owner request returned `202`; a rapid second request returned `429` and the browser could read `Retry-After: 300` after the CORS fix. |
| CORS | **PASS** | Real browser-origin API requests from `http://localhost:5173` to `http://localhost:8084` succeeded. The configured origin now exposes `Retry-After` in addition to `X-Request-Id`. |
| Reindex authorization and bounds | **PARTIAL** | Service tests cover MEMBER denial, the 501-active-document rejection before limiter acquisition, and bounded retry exhaustion to `FAILED`. Owner `202` and cooldown `429` were observed live; MEMBER `403`, over-limit `413`, and exhausted worker retries were not exercised as live HTTP flows. |
| Retrieval evaluation | **PASS ON THE FIXTURE** | [`docs/evaluation/v3-local-2026-11-09.json`](evaluation/v3-local-2026-11-09.json) records 13 synthetic document revisions and 23 labeled queries (15 positive, 8 negative), using `intfloat/multilingual-e5-small` and cutoff `0.81`: Recall@5 `1.0`, Hit@5 `1.0`, MRR@5 `0.9333`, negative queries empty `8/8`. Lowest positive score is `0.8265`; highest negative candidate is `0.794`. These small synthetic results are regression evidence, not a production quality claim. |
| Frozen V1 contract | **PASS** | No API contract change and no edits to migrations V1–V7. V8 remains the only V3 migration. |
| Secret handling | **PASS** | `.env.v3` remains ignored and untracked. No local credential values are recorded in repository files. |
| `git diff --check` | **PASS** | Re-run after final documentation/commit changes. |

## Remaining acceptance gates

- [x] Push the scoped follow-up and confirm all three workflows pass on PR #10 head `01bc9f6`, including backend Testcontainers and Java 21 image verification.
- [ ] Run live HTTP checks for MEMBER `403`, the over-500-document `413`, and retry exhaustion to `FAILED`; existing service tests cover these rules but are not a substitute for live integration evidence.
- [ ] Keep PR #10 open for human review. Do not merge it or start V4 as part of this acceptance task.

Local `mvn clean verify` remains blocked by this Docker Desktop/Testcontainers socket metadata issue, while the same Testcontainers integration passes in current-head CI. Treat CI as the integration result; do not report the local verify command as passing.

## Scope and data boundary

- MySQL is the source of truth; Qdrant data is derived and rebuildable.
- Spring owns authentication, authorization, and current-document/revision checks. The Python service is private and accepts only internal-token requests.
- Embeddings and generation use local E5 and Ollama; no paid AI API or cloud inference is configured.
- V1 API contract and migrations V1–V7 are frozen. V4 Agent work has not started.
