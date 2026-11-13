# OpsPilot — Project Roadmap & Team Guide

> **Updated:** 2026-11-13
> **Current Milestone:** V3 Knowledge / RAG — in progress
> **Overall Progress:** V1 and V2 are merged into `main`. V3 Knowledge/RAG implementation is on `feat/v3-knowledge-rag`; implementation SHA `dad1787511a54758b86f80d08054176ba930c0a4` has all three CI workflows passing. Three live HTTP boundary checks remain unverified because Docker Desktop's daemon was unavailable during final acceptance; see `docs/V3_ACCEPTANCE.md`.
> **Current Sprint:** V3 final acceptance evidence for authorization, document limit, and retry exhaustion
> **Next Major Milestone:** Complete the three live V3 HTTP boundary checks and PR #10 human review; do not start V4

## 1. Project goal

OpsPilot is an AI-native team operations platform.

The project is deliberately built in layers:

1. Build a real working SaaS product first.
2. Make the backend production-like.
3. Add knowledge retrieval/RAG.
4. Add Agent + Tool Calling.
5. Add Evaluation, Observability and Context Engineering.
6. Add MCP/Memory.
7. Finish deployment, performance, CI/CD and resume/demo packaging.

The final goal is **not** a chat UI with an LLM attached. The AI layer must operate inside a real system with authentication, RBAC, transactions, tasks, documents, audit history and reliable service boundaries.

## 2. Current progress

| Phase | Scope | Status |
|---|---|---|
| 1 | Java 21 / Spring Boot / MySQL / Flyway / Docker Compose / Health | ✅ Complete |
| 2 | User Registration / BCrypt / Validation | ✅ Complete |
| 3 | Login / JWT / Spring Security / `/api/users/me` | ✅ Complete |
| 4 | Workspace / Membership / OWNER-ADMIN-MEMBER / Authorization | ✅ Complete |
| 5 | Project Management / ACTIVE-ARCHIVED | ✅ Complete |
| 6 | Task / Status / Priority / Assignee / Due Date | ✅ Complete |
| 7 | Workspace Documents / Knowledge Base foundation | ✅ Complete |
| 8 | Activity Logs + Dashboard Aggregation | ✅ Complete |
| V2 | Production configuration / Redis auth protection / observability / service reliability | ✅ Complete and merged through PR #9; see `docs/V2_ACCEPTANCE.md` |
| V3 | Local workspace knowledge retrieval and grounded answers | 🚧 In progress on `feat/v3-knowledge-rag`; see `docs/V3_ACCEPTANCE.md` |

Phases 1–8 have been:
- implemented,
- automatically tested,
- verified with real MySQL/JWT/HTTP flows,
- committed and pushed.

## 3. Phase 8 — complete

Phase 8 has been implemented, automatically tested, verified against real MySQL/Flyway V7, and exercised through JWT-authenticated HTTP flows. Verified behavior includes transactional activity recording, feed limits and ordering, dashboard aggregation, overdue-task rules, archive idempotency, tenant isolation, and persisted activity rows.

The V1 backend feature implementation is complete and merged. Its API contract is frozen for V1. V2 is merged; V3 local Knowledge/RAG is now underway as a separate milestone.

The Phase 1–8 backend API contract is frozen for V1 except for bug fixes or explicitly agreed contract changes. The shared frontend reference is `docs/API_CONTRACT.md`.

## 3.1 Frontend and V1 acceptance status

- Frontend authentication and the F2 workspace/dashboard foundation are present on `main`; PR #6 is merged. Existing contributor commits remain intact.
- Human browser acceptance for F1 was reported complete by Joan. Do not extend that result to later features.
- The merged V1 completion includes the F2 loading/session-race fixes, Projects and Tasks views, workspace Documents views, regression tests, and a frontend CI test step.
- Previously reported local verification passed: frontend `npm test` (26 tests), lint, production build, and backend `mvn clean verify` (38 tests). The backend compiled with `--release 21` using the locally available Java 26 Maven runtime; Java 21 was not installed locally. Backend CI is configured for Temurin Java 21.
- Previously reported isolated MySQL 8.4 and Spring Boot integration passed Flyway V1–V7, health, 28 authenticated HTTP checks, and headless Chrome flows (81 API responses), including auth restoration, tenant isolation, workspace switching, project/task/document writes, Dashboard activity, CORS, and a simulated project-list network failure with Retry. Separately, human F1 acceptance reported backend-unavailable UX and login after service recovery as passing.
- Temporary services and data were removed after verification; the pre-existing `opspilot-mysql` container and `opspilot_mysql_data` volume remained healthy and untouched.
- Consolidated [PR #7](https://github.com/JoanH-project/opspilot/pull/7) was merged into `main` on 2026-11-06 at merge commit `1c4e65d`. Frontend CI run [42184723079](https://github.com/JoanH-project/opspilot/actions/runs/42184723079) passed on its final head `3ce79d7` (install, 26 tests, lint, and build).
- V1 is complete and frozen. V2 Production Backend is merged through PR #9. V3 Knowledge/RAG is in progress on `feat/v3-knowledge-rag`; see `docs/V3_ACCEPTANCE.md` for current evidence and acceptance gaps.

### V1 post-merge follow-ups

These items remain visible for separate decisions or verification; they did not block the V1 merge:

- Contract decision: the backend task enum accepts `URGENT`, while the frozen frontend contract lists `LOW`, `MEDIUM`, and `HIGH`; the UI follows the frozen contract.
- Dependency review: V1 was reported with 0 production advisories and 13 development advisories (10 moderate, 3 high). V2 has applied non-force in-range fixes for current high advisories; 10 moderate development advisories remain as recorded in `docs/V2_ACCEPTANCE.md`.
- Auth regression R4 has a passing frontend component test for login success followed by `/api/users/me` failure and no token persistence. A real browser check was not run in this session. R1–R3 were reported as passing during human F1 acceptance.
- The consolidated F2–F4 browser run simulated a project-list failure for Retry coverage; the separate human F1 acceptance covered backend-unavailable UX and service recovery.
- Local backend verification used Java 26 with the Java 21 release target because JDK 21 was unavailable on that machine. The backend CI workflow is configured with Temurin Java 21.

### Phase 8 completion rule

Activity logging must participate in the same transaction as the business operation:

```text
Business operation
      ↓
Persist domain change
      ↓
Persist ActivityLog
      ↓
COMMIT
```

If the ActivityLog write fails, the business operation must roll back. Idempotent operations such as archiving an already archived resource must not create misleading duplicate activity records.

## 3.2 V2 status

V2 Production Backend was merged through PR #9 into `main` on 2026-11-09. Its verification record, including CI-backed Testcontainers and the separate local integration evidence, remains in [docs/V2_ACCEPTANCE.md](docs/V2_ACCEPTANCE.md). No V1 API contract, workspace authorization rule, or V1–V7 migration was changed. The `URGENT` task priority enum difference and remaining development-only npm advisories remain follow-up decisions, not changes introduced by V3.

## 3.3 V3 status

V3 implementation is on `feat/v3-knowledge-rag`, based on the merged V2 `main`, and PR #10 is open but not merged. It adds V8 only, a private local Python retrieval service, Qdrant, local Ollama answer generation, and an authenticated Knowledge page. MySQL and Spring remain the source of truth and authorization boundary. The prior isolated E5/Qdrant/Ollama/Spring flow, browser flows, and 13-revision/23-query **synthetic regression fixture** pass; this small fixture is not evidence of production retrieval quality. It records Recall@5/Hit@5 1.0, MRR@5 0.9333, and 8/8 negative queries empty at cutoff 0.81. Reindex CORS exposes Retry-After. On exact PR head `dad1787511a54758b86f80d08054176ba930c0a4`, [Backend CI](https://github.com/JoanH-project/opspilot/actions/runs/42602909666), [AI Service CI](https://github.com/JoanH-project/opspilot/actions/runs/42602909619), and [Frontend CI](https://github.com/JoanH-project/opspilot/actions/runs/42602909697) passed; Backend CI includes Testcontainers and Java 21 checks. This final acceptance attempt could not reach the isolated services because Docker Desktop's daemon socket did not answer bounded probes; therefore real HTTP MEMBER `403`, over-limit `413`, and retry exhaustion to persisted `FAILED` remain unverified. The prior local `mvn clean verify` Testcontainers issue also remains; CI is not being represented as a substitute for those three HTTP checks. V3 remains `PARTIAL`, not ready for review; see [docs/V3_ACCEPTANCE.md](docs/V3_ACCEPTANCE.md) and [docs/evaluation/v3-http-boundary-2026-11-13.md](docs/evaluation/v3-http-boundary-2026-11-13.md). Preserve the frozen V1 API, V1–V7 migrations, V8 design boundary, and local E5/Ollama zero-paid-API design.

## 4. Current product architecture

```text
React + TypeScript
Login / Dashboard / Projects / Tasks / Documents
                  │
                  │ REST
                  ▼
Spring Boot
Auth / JWT / RBAC
Workspace / Project / Task / Document
Activity / Dashboard / Business Services
                  │
                  ▼
                MySQL

---------------- V3 Knowledge Layer (in progress) ----------------

MySQL document + transactional index state
   ↓
Spring index worker → private FastAPI service
   ↓
Markdown chunks → multilingual E5 embeddings → tenant-filtered Qdrant
   ↓
Spring verifies workspace + ACTIVE document + current revision
   ↓
Local Ollama answer with verified citations

---------------- Future V4+ ----------------
Agent tools → Evaluation / Observability → MCP / Memory
```

## 5. Version roadmap

### V1 — Full-stack Foundation

Goal: a complete demonstrable product.

- Spring Boot backend
- MySQL + Flyway
- JWT + Spring Security
- Workspace + RBAC
- Projects
- Tasks
- Documents
- Activity Log
- Dashboard aggregation
- React + TypeScript frontend
- Login/Register UI
- Dashboard UI
- Projects UI
- Project Detail / Tasks UI
- Documents UI
- Frontend/backend integration
- Docker-based local demo

**Current version: V1 complete and frozen; V2 merged; V3 Knowledge/RAG is in progress.**

### V2 — Production Backend

Goal: secure, repeatable production-like backend operation without changing the V1 business contract. Current scoped design:

- Strong fail-fast `prod` profile for JWT, MySQL TLS, exact CORS, Redis TLS, and management binding
- Redis Lua-backed shared limits for login and registration only
- Protected Actuator health and Prometheus metrics on a separate management listener
- Bounded request ID and privacy-safe request logs
- Separate local Docker demo and MySQL/Redis integration verification
- V1 R4 component regression; browser status recorded honestly

No queue, async job, SSE, broad caching, Kubernetes, cloud service, or AI behavior is included because V2 has no workload requiring them.

### V3 — Knowledge AI / RAG

**Status: in progress.** See `docs/V3_ARCHITECTURE.md`, `docs/V3_API.md`, `docs/V3_EVALUATION.md`, and `docs/V3_ACCEPTANCE.md`.

```text
Document → Transactional index outbox → Chunk → E5 embedding → Qdrant retrieval → verified evidence → local Ollama answer
```

V3 keeps MySQL as source of truth and Spring as the authorization boundary. It adds a private Python service, tenant-filtered Qdrant, local answer generation, server-verified citations, a small synthetic retrieval corpus, and an isolated local Docker stack. Do not mark V3 complete until the full local model and HTTP acceptance is observed.

### V4 — Agent

Agent works through real OpsPilot business capabilities and permissions.

Initial tools may include:

```text
search_documents()
query_tasks()
create_task()
update_task()
search_members()
```

The Agent must not bypass RBAC/service rules by directly changing database state.

### V5 — AI Engineering

Focus:
- Evaluation
- Structured Output
- Trace / Observability
- Context Engineering
- Tool-call reliability
- Failure recovery
- Security / authorization
- Cost / latency awareness

### V6 — MCP + Memory

Focus:
- MCP server/tools
- Agent memory
- Context management
- External clients/tools integration

### V7 — Deployment + Performance

Focus:
- CI/CD
- Docker / Kubernetes where justified
- Load testing
- Monitoring
- Performance optimization
- Production deployment
- Final demo, architecture and resume packaging

## 6. Recruiting-trend priorities

The project direction remains unchanged, but later phases should emphasize:

| Area | Priority | OpsPilot |
|---|---:|---|
| Java / Spring Boot | ★★★★★ | V1/V2 backend |
| MySQL / Redis / distributed systems | ★★★★★ | V1/V2 |
| Python | ★★★★★ | V3+ |
| RAG | ★★★★★ | V3 |
| Agent / Tool Calling | ★★★★★ | V4 |
| Evaluation | ★★★★★ | V3–V5 |
| Observability | ★★★★★ | V2/V5/V7 |
| Context Engineering | ★★★★★ | V4/V5 |
| Workflow / Agent Infra | ★★★★★ | V4/V5 |
| Docker / Kubernetes | ★★★★★ | V2/V7 |
| React / TypeScript | ★★★★☆ | V1 frontend |
| MCP | ★★★★☆ | V6 |
| AI Coding / Harness Engineering | ★★★★☆ | development workflow |

Rule: **do not skip backend/frontend engineering just because Agent work is trending. The goal is to build Agent capability into a real product.**

## 7. Team structure

Until contributor names are finalized, use:

- **Joan — Owner / Architecture / Backend / AI Integration**
- **Member A — Frontend / Product Experience**
- **Member B — QA / DevOps / Engineering Enablement**

### Joan

Primary ownership:
- Product/technical direction
- Spring Boot core domain and authorization
- Data model / Flyway review
- Codex phase implementation review
- RAG/Agent architecture
- Cross-module integration
- Merge/release gate
- Resume/demo architecture story

Current tasks:
- [x] Preserve merged backend Phases 1–8 and the frozen V1 API contract
- [x] Preserve merged F1/F2 frontend work and contributor history
- [x] Finish owner-led F2 hardening, F3 Projects/Tasks, and F4 Documents
- [x] Run frontend test/lint/build and backend verification on the current branch head
- [x] Complete safe local MySQL/HTTP integration and headless Chrome browser checks
- [x] Review PR #7 and complete the owner merge decision; merged to `main` as `1c4e65d`

### Member A — Frontend / Product Experience

Frontend F1/F2 and the initial frontend foundation are complete and merged. F2 hardening and the F3/F4 product flows were completed in PR #7.

Primary ownership:
- React + TypeScript frontend
- App shell/navigation
- API client layer
- Login/Register
- Dashboard
- Projects
- Project Detail / Task UI
- Documents
- Loading / Empty / Error states
- Responsive/accessibility
- Later Agent/Knowledge UX

The initial F1/F2 task list is complete. Do not start V2 frontend work until its scope is explicitly defined.

Do not:
- change backend contracts without agreement,
- add AI chat now,
- add speculative state management,
- add many dependencies without need.

### Member B — QA / DevOps / Engineering Enablement

The Q3 auth regression checklist was merged in PR #5. Its R4 browser verification remains an open QA handoff in `TEAM_STATUS.md`; assign any additional QA/DevOps work as a scoped task.

Primary ownership:
- Fresh-clone verification
- Docker dev environment
- API smoke tests
- CI
- README/onboarding quality
- V1 integration acceptance checks
- Later observability/deployment

Do not:
- add Kubernetes now,
- add Redis/MQ before V2,
- refactor business code without scope,
- build monitoring infrastructure prematurely.

## 8. Git / Codex workflow

Every implementation task follows:

```text
Read AGENTS.md
      ↓
Read PROJECT_ROADMAP.md
      ↓
Understand existing code/tests/migrations
      ↓
Take one scoped task
      ↓
Implement
      ↓
Tests / build
      ↓
Human review
      ↓
Integration verification when required
      ↓
Update TEAM_STATUS.md
      ↓
Commit
      ↓
Push / PR
```

### Branching

- `main` must remain runnable.
- Each member works on a feature branch.
- One coherent task per PR.
- Do not combine unrelated changes.

Examples:

```text
feat/frontend-auth
feat/frontend-dashboard
test/api-smoke-suite
ci/backend-checks
docs/onboarding
```

## 9. Shared GitHub files — what each file is for

### `AGENTS.md`

**Long-lived engineering constitution.**

Contains durable rules such as:
- simple solutions first,
- no speculative architecture,
- work only on the current scope,
- understand existing code before modifying,
- verify before claiming completion,
- no fake/placeholder implementations.

Members do **not** edit this after every task. Update it only when a new durable project-wide engineering rule is agreed.

### `PROJECT_ROADMAP.md`

**Shared master roadmap and architecture/status overview.**

This file answers:
- What is OpsPilot?
- Where are we now?
- What are the versions/phases?
- Who owns which area?
- What is the current sprint?
- Has the overall direction changed?

Update it when:
- a Phase completes,
- a milestone changes,
- team ownership changes,
- roadmap/version scope changes,
- a meaningful recruiting/technical-priority change affects the project.

Members should not append daily task logs here.

### `TEAM_STATUS.md`

**The one file every member updates when an individual assigned task is finished.**

Each completed task should update the member's row with:
- Task
- Branch / PR
- Status
- Verification
- Handoff / remaining issue
- Date

This is the daily/weekly collaboration surface.

### `README.md`

**How to run and use the repository.**

Update it when:
- setup changes,
- environment variables change,
- public API usage changes,
- developer onboarding commands change.

Do not use README as a project diary.

### Private prompts

Codex implementation prompts can remain private between Joan and the assignee. They do not need to be committed to GitHub unless the team later decides to maintain reusable issue templates/specs.

## 10. V1 completion record

The previously merged Member A and Member B work was retained. The consolidated V1 completion was merged through PR #7 and documentation closeout PR #8 on 2026-11-07 (`c9c6ed3`). V2 was merged through PR #9; V3 is tracked on `feat/v3-knowledge-rag`.

### Completed checkpoints

1. [x] Fix the known F2 first-load and session-change regressions.
2. [x] Complete project list/create/edit/archive and project-scoped task flows.
3. [x] Complete workspace document list/create/detail/edit/preview/archive.
4. [x] Run frontend tests, lint, build, backend verification, and available isolated local integration checks.
5. [x] Update the smoke checklist, README, `TEAM_STATUS.md`, and this roadmap with observed results.
6. [x] Pass final Frontend CI on the PR head and merge the consolidated PR into `main`.

## 11. V1 page scope

Only core pages:

1. Login / Register
2. Dashboard
3. Projects
4. Project Detail
5. Tasks
6. Documents

Activity Feed is part of Dashboard in V1.

V1 does not include:
- AI chat
- RAG UI
- Agent UI
- MCP UI
- Workflow builder
- collaborative document editor
- microservice admin tooling

## 12. Definition of Done

A task is complete only when:

- [ ] Scope matches the assigned task/Phase
- [ ] No future features were added accidentally
- [ ] Behavior tests were added/updated where needed
- [ ] Relevant tests pass
- [ ] Build/type check passes
- [ ] Required integration verification passes
- [ ] README/shared docs are updated when behavior changed
- [ ] No placeholders/fake implementations/dead code
- [ ] Human review is complete
- [ ] `TEAM_STATUS.md` is updated
- [ ] Commit/PR is clear
- [ ] Push/merge is complete as appropriate

Codex saying "implemented" is not itself completion.

## 13. Next milestones

### Milestone 1 — Backend Phases 1–8

Complete and merged. The backend API remains frozen except for verified bugs and explicitly agreed contract changes.

### Milestone 2 — Frontend F1/F2 foundation

F1 authentication and F2 workspace/dashboard implementation are merged. Preserve their history and test coverage.

### Milestone 3 — V1 full-stack completion

Complete and merged to `main` through PR #7 on 2026-11-06. Final Frontend CI passed on the PR head. The remaining verification and contract/dependency decisions are listed under §3.1; they do not change V1's completed status. V2 and V3 are tracked as separate milestones.

### V1 Full-stack demo flow

```text
Register/Login
      ↓
Dashboard
      ↓
Projects
      ↓
Project Detail / Tasks
      ↓
Documents
      ↓
Activity
```

At this milestone OpsPilot becomes a demonstrable product, not only a backend project.

## 14. New-member onboarding

1. Read `AGENTS.md`
2. Read `PROJECT_ROADMAP.md`
3. Read `TEAM_STATUS.md`
4. Read `README.md`
5. Start the project locally
6. Run existing tests/build
7. Check assigned track/task
8. Create a feature branch
9. Implement + verify
10. Update `TEAM_STATUS.md`
11. Open PR / request review

If documentation conflicts with verified merged code/migrations/APIs, current verified code is the factual source; fix the documentation in the same workstream.
