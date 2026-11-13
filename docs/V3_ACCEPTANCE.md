# V3 Knowledge / RAG — Acceptance Record

- **Updated:** 2026-11-13 (Australia/Sydney)
- **Status:** `PARTIAL — latest code/CI configuration CI passes; three requested live HTTP boundary checks remain blocked`
- **Base:** `f1e38a0b339f763368a63d2c6a5a297f0f8f11ee` (PR #9 merge on `origin/main`)
- **Branch:** `feat/v3-knowledge-rag`
- **PR:** [#10](https://github.com/JoanH-project/opspilot/pull/10), open to `main`, not merged
- **Latest verified V3 implementation SHA:** `dad1787511a54758b86f80d08054176ba930c0a4` (`dad1787`). Backend, AI Service, and Frontend CI all passed on this exact implementation SHA. Backend CI ran the MySQL/Redis Testcontainers integration and built the Java 21 image. The later acceptance-only commit `1443fd6` did not change application code, but its Backend CI failed because the `ubuntu-latest` image had moved to a Docker daemon requiring API 1.40 while Testcontainers requested API 1.32. Backend CI is now pinned to `ubuntu-24.04`; rerun results on the updated workflow are pending.
- **Latest verified code/CI configuration SHA:** `edb283477353a0520b32e2ca57a922fa2dae10bd` (`edb2834`). Backend, AI Service, and Frontend CI all passed on this SHA. Backend CI's Testcontainers integration and Java 21 image build/runtime steps passed after pinning the job to Ubuntu 24.04. This SHA adds only the CI runner pin and acceptance documentation; V3 application code remains unchanged.

This record separates local and browser observations from CI results. V3 remains in progress. Do not merge or start V4 until the remaining checks and review gates are resolved.

## Verification observed locally

| Area | Result | Evidence |
|---|---|---|
| AI service lock, lint, and tests | **PASS** | `uv lock --check`; Ruff passed; 18 pytest tests passed. Embedded Qdrant warns that payload indexes are inactive in local mode; the server-Qdrant test remains covered by CI. |
| Frontend lint, tests, and build | **PASS** | The unchanged frontend passed lint, 32 tests in 5 files, and Vite production build. No frontend source was changed during this follow-up. |
| Backend unit tests and package | **PASS** | `mvn clean package`: 55 tests, no failures/errors/skips; Java compiler release target 21. Docker rebuilt the isolated backend image successfully from its Java 21 build image. |
| Backend Testcontainers integration | **PASS IN CI; BLOCKED LOCALLY** | On prior local verification, `mvn clean verify` passed unit tests but Testcontainers could not validate this Docker Desktop daemon: the selected socket returned HTTP 400 with empty daemon metadata. GitHub Backend CI passed the Testcontainers integration on current head. In this acceptance attempt, the Docker daemon did not answer bounded CLI probes, so no local Maven verification was rerun. |
| PR #10 code CI | **PASS on latest verified implementation SHA** | At `dad1787511a54758b86f80d08054176ba930c0a4`, [Backend CI](https://github.com/JoanH-project/opspilot/actions/runs/42602909666), [AI Service CI](https://github.com/JoanH-project/opspilot/actions/runs/42602909619), and [Frontend CI](https://github.com/JoanH-project/opspilot/actions/runs/42602909697) all passed. Backend CI includes Testcontainers integration and Java 21 image/runtime verification. Earlier failed heads were corrected by making the acceptance-test helper read top-level IDs from object and array responses. |
| CI runner compatibility follow-up | **PASS on `edb2834`** | The docs-only head `1443fd6` exposed an Ubuntu runner/Docker API mismatch: [Backend CI run 43374135597](https://github.com/JoanH-project/opspilot/actions/runs/43374135597) failed with daemon minimum API 1.40 versus Testcontainers API 1.32; its [AI Service](https://github.com/JoanH-project/opspilot/actions/runs/43374135506) and [Frontend](https://github.com/JoanH-project/opspilot/actions/runs/43374135550) runs passed. Backend CI now pins Ubuntu 24.04. On exact SHA `edb283477353a0520b32e2ca57a922fa2dae10bd`, [Backend CI run 43374514912](https://github.com/JoanH-project/opspilot/actions/runs/43374514912), [AI Service CI run 43374514982](https://github.com/JoanH-project/opspilot/actions/runs/43374514982), and [Frontend CI run 43374514833](https://github.com/JoanH-project/opspilot/actions/runs/43374514833) all passed. Backend job steps include Testcontainers integration, Java 21 image build, and runtime confirmation. |
| Isolated V3 stack and migrations | **PASS in prior run; unavailable in this attempt** | The prior isolated MySQL 8.4, Redis, Qdrant, Ollama, AI service, and Spring backend run passed Flyway V1–V8, Hibernate validation, and `GET /api/health` (`200 {"status":"UP"}`). During this final acceptance attempt, Docker Desktop's selected daemon socket did not answer; backend port 8084 refused connections. No boundary fixtures or requests were created. The original MySQL container and volume were not modified. See [`docs/evaluation/v3-http-boundary-2026-11-13.md`](evaluation/v3-http-boundary-2026-11-13.md). |
| Browser registration and workspace flow | **PASS** | Automated browser session registered/logged in, created a workspace and document, indexed it, and used workspace-scoped search. This is agent-controlled browser evidence, not human acceptance. |
| Grounded Ask and safety | **PASS, with latency caveat** | Ask returned the current runbook answer and citation while ignoring an instruction embedded in the document. An unsupported private-address question returned `NOT ENOUGH EVIDENCE` without sources. Under concurrent model/evaluation load one Ask returned a sanitized 503; retry after the stack was idle succeeded in 32.1 seconds. |
| Reindex and cooldown | **PASS** | Browser-origin owner request returned `202`; a rapid second request returned `429` and the browser could read `Retry-After: 300` after the CORS fix. |
| CORS | **PASS** | Real browser-origin API requests from `http://localhost:5173` to `http://localhost:8084` succeeded. The configured origin now exposes `Retry-After` in addition to `X-Request-Id`. |
| Reindex authorization and bounds | **PARTIAL — live checks blocked** | Service tests cover MEMBER denial, the 501-active-document rejection before limiter acquisition, and bounded retry exhaustion to `FAILED`. Owner `202` and cooldown `429` were observed live; the requested MEMBER `403`, over-limit `413`, and exhausted worker retries were not exercised as live HTTP flows because the local Docker daemon was unavailable before setup. No result is claimed for these three checks. |
| Retrieval evaluation | **PASS ON THE FIXTURE** | [`docs/evaluation/v3-local-2026-11-09.json`](evaluation/v3-local-2026-11-09.json) records 13 synthetic document revisions and 23 labeled queries (15 positive, 8 negative), using `intfloat/multilingual-e5-small` and cutoff `0.81`: Recall@5 `1.0`, Hit@5 `1.0`, MRR@5 `0.9333`, negative queries empty `8/8`. Lowest positive score is `0.8265`; highest negative candidate is `0.794`. These small synthetic results are regression evidence, not a production quality claim. |
| Frozen V1 contract | **PASS** | No API contract change and no edits to migrations V1–V7. V8 remains the only V3 migration. |
| Secret handling | **PASS** | `.env.v3` remains ignored and untracked. No local credential values are recorded in repository files. |
| `git diff --check` | **PASS** | Re-run after final documentation/commit changes. |

## Remaining acceptance gates

- [x] Confirm all three workflows passed on implementation SHA `dad1787511a54758b86f80d08054176ba930c0a4`, including Backend Testcontainers and Java 21 image verification.
- [ ] Run live HTTP checks for MEMBER `403`, the over-500-document `413`, and retry exhaustion to `FAILED`; these were blocked before any requests because the local Docker daemon did not answer. Existing service tests are not a substitute for live integration evidence.
- [ ] Keep PR #10 open for human review. Do not merge it or start V4 as part of this acceptance task.

PR #10 is **not yet ready for human review**: it remains `PARTIAL` until the three live HTTP checks pass. The runner compatibility fix is verified on `edb2834`; the application implementation CI also passed on `dad1787`. Local `mvn clean verify` remains blocked by the Docker Desktop/Testcontainers socket metadata issue. Do not report local verify as passing.

## Scope and data boundary

- MySQL is the source of truth; Qdrant data is derived and rebuildable.
- Spring owns authentication, authorization, and current-document/revision checks. The Python service is private and accepts only internal-token requests.
- Embeddings and generation use local E5 and Ollama; no paid AI API or cloud inference is configured.
- V1 API contract and migrations V1–V7 are frozen. V4 Agent work has not started.
