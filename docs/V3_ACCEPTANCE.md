# V3 Knowledge / RAG — Acceptance Record

- **Updated:** 2026-11-13 (Australia/Sydney)
- **Status:** `V3_READY_FOR_HUMAN_REVIEW`
- **Branch:** `feat/v3-knowledge-rag`
- **PR:** [#10](https://github.com/JoanH-project/opspilot/pull/10), OPEN, not merged
- **V3 HTTP acceptance code SHA:** `b8adb8903724f918ecb518da8114fe38a0c4a2b2`
- **PR base:** `f1e38a0b339f763368a63d2c6a5a297f0f8f11ee` (`main`)

All three requested HTTP boundaries and all required CI workflows passed for the SHA above. The Backend PR workflow ran on GitHub's Ubuntu 24.04 runner with Java 21 and the PR merge ref containing that head. Backend ran Spring Boot on a random HTTP port with real JWT, MySQL 8.4 Testcontainers, Redis 7.4 Testcontainers, Flyway V1–V8, JPA/Hibernate, workspace authorization, limiter, and the scheduled index Worker. The **only test substitute** was a localhost-bound HTTP server for the private AI dependency; it checks the internal service token and can return controlled 503 or success responses. No production retry setting or maximum attempt count was changed.

## Three real HTTP boundary results

`V3KnowledgeHttpBoundaryIT` ran against the running Spring Boot server using `TestRestTemplate`; no MockMvc or direct controller invocation was used for the acceptance requests. All identities, workspaces, and documents were synthetic and lived only in the runner's temporary database.

| Test | Observed HTTP and sanitized response evidence | Database / Worker assertions | Result |
|---|---|---|---|
| `memberCannotReindexOrAffectWorkspaceTasksAndCooldown` | Owner reindex `202` (`{"queuedDocuments":1,"status":"QUEUED"}`); MEMBER on that workspace `403` (`{"status":403,"error":"Forbidden","message":"Insufficient workspace permissions"}`); non-member on another workspace `404`; owner on control workspace `202`. | Target index row count and limiter timestamp unchanged across MEMBER `403`; no AI Worker request from the denied operation; the other workspace remained independently operable. Membership was seeded as a real MEMBER row after the owner and member were registered/logged in over HTTP. | **PASS** |
| `fiveHundredAndOneActiveDocumentsReturn413BeforeTaskOrCooldownWrites` | Database count was 501 ACTIVE documents. Two real owner reindex requests both returned `413` with `{"status":413,"error":"Payload Too Large","message":"Workspace reindex exceeds the supported document limit","fieldErrors":{}}`. | No index-state rows and no limiter row after either `413`; after deleting only the synthetic documents, the same workspace returned `202`; a separate control workspace also returned `202`. No AI inference task was created. | **PASS** |
| `realWorkerPersistsEightFailuresThenStopsAndProcessesAfterAiRecovery` | Real reindex HTTP returned `202` (`queuedDocuments=1`, `status=QUEUED`); index-status HTTP returned `200`. The test AI endpoint returned controlled `503` for each Worker call. | Actual scheduled Worker made 8 calls; persisted attempts reached exactly 8. Attempts 1–7 had a next retry time; terminal state was `FAILED`, `next_attempt_at=NULL`, `lease_until=NULL`, and `last_error_summary=INDEXING_FAILED`. A 1-second wall-clock observation showed no ninth call. After the stub returned success, a new HTTP-accepted task reached `READY`. The test-only Clock advanced the production backoff schedule by about 10m15s in simulation; the JUnit test method took 1.853s wall time. Production backoff and `MAX_ATTEMPTS=8` were unchanged. | **PASS** |

The Backend workflow emitted the expected route/status logs without logging JWTs, passwords, or the AI error sentinel. Its [JUnit report artifact](https://github.com/JoanH-project/opspilot/actions/runs/43378130105/artifacts/14015283475) records 3 tests, 0 failures, 0 errors, and 0 skipped (suite time 12.193s). The worker test method took 1.853s. GitHub retains this report artifact for 7 days.

## CI for the acceptance code SHA

- [Backend CI run 43378130105](https://github.com/JoanH-project/opspilot/actions/runs/43378130105) — **PASS**: `mvn clean verify`, existing MySQL/Redis Testcontainers integration, the three V3 HTTP acceptance tests, Java 21 image build and runtime confirmation, and JUnit artifact upload.
- [AI Service CI run 43378130078](https://github.com/JoanH-project/opspilot/actions/runs/43378130078) — **PASS**: lint, Qdrant-backed tests, and image build.
- [Frontend CI run 43378129996](https://github.com/JoanH-project/opspilot/actions/runs/43378129996) — **PASS**: tests, lint, and build.

All runs are associated with PR head `b8adb8903724f918ecb518da8114fe38a0c4a2b2`; the Backend PR job checked out the corresponding merge ref `f55b67666fb260187579b52ff76f009501454825`. Testcontainers started temporary `mysql:8.4` and `redis:7.4-alpine` containers on the runner. Testcontainers stopped their containers after the class and the hosted runner was discarded after the job. No local database, existing MySQL container/volume, production credential, or paid AI service was used.

## Other V3 evidence and limits

- Prior browser evidence: registration/login, indexing, search and cited answers passed; prompt-like document text did not override instructions; an unsupported query returned `NOT ENOUGH EVIDENCE`. Owner reindex returned `202`, a rapid repeat returned `429`, and browser code could read `Retry-After: 300`. These are prior observed flows, separate from the three CI boundary tests.
- [`docs/evaluation/v3-local-2026-11-09.json`](evaluation/v3-local-2026-11-09.json) contains a small **synthetic regression fixture** (13 document revisions, 23 queries): Recall@5 `1.0`, Hit@5 `1.0`, MRR@5 `0.9333`, and 8/8 negative queries empty at cutoff `0.81`. This is not a production corpus and does not guarantee production retrieval quality.
- This machine's Docker Desktop daemon remains unresponsive. Local Testcontainers/`mvn clean verify` was not run or claimed as passing in this task. Local `mvn -DskipTests test-compile` passed, and `mvn test` passed 55 unit tests. This local environment maintenance issue is independent of the successful isolated CI acceptance.
- The current Backend CI runner pin to Ubuntu 24.04 resolves the hosted Testcontainers Docker API compatibility issue observed on a prior runner image.
- No V1 API contract or V1–V7 migration changed. V8 remains the V3 knowledge migration. Qdrant remains derived/rebuildable data; embeddings and generation use local E5/Ollama with no paid inference API.

## Review handoff

V3 is `V3_READY_FOR_HUMAN_REVIEW`. PR #10 remains OPEN and unmerged for human code review. Do not merge it or begin V4 as part of this acceptance handoff.
