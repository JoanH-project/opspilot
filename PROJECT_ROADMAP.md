# OpsPilot — Project Roadmap & Team Guide

> **Updated:** 2026-08-25
> **Current Milestone:** V1 Backend Freeze / Frontend Integration
> **Overall Progress:** OpsPilot complete target ≈ 42%; V1 ≈ 65–70%; V1 backend feature implementation ≈ 100%
> **Current Sprint:** Frozen V1 backend contract and React/TypeScript frontend integration
> **Next Major Implementation:** React + TypeScript frontend

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

Phases 1–8 have been:
- implemented,
- automatically tested,
- verified with real MySQL/JWT/HTTP flows,
- committed and pushed.

## 3. Phase 8 — complete

Phase 8 has been implemented, automatically tested, verified against real MySQL/Flyway V7, and exercised through JWT-authenticated HTTP flows. Verified behavior includes transactional activity recording, feed limits and ordering, dashboard aggregation, overdue-task rules, archive idempotency, tenant isolation, and persisted activity rows.

The V1 backend feature implementation is essentially complete. The next step is V1 Backend Freeze and frontend API contract review; no later backend or AI phase has started.

The Phase 1–8 backend API contract is frozen for V1 except for bug fixes or explicitly agreed contract changes. The shared frontend reference is `docs/API_CONTRACT.md`.

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

---------------- Future AI Layer ----------------

Document
   ↓
Chunking
   ↓
Embedding
   ↓
Vector Retrieval
   ↓
RAG
   ↓
Agent
 ├─ search_documents
 ├─ query_tasks
 ├─ create_task
 └─ update_task
   ↓
Evaluation / Observability
   ↓
MCP / Memory
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

**Current version: V1**

### V2 — Production Backend

Add only where there is a concrete scenario:

- Redis
- Message queue
- Async jobs
- SSE/streaming where justified
- Rate limiting
- Retry/failure handling
- Stronger observability
- Docker/service reliability improvements

### V3 — Knowledge AI / RAG

```text
Document → Chunk → Embedding → Vector Store → Retrieval → LLM
```

Focus:
- Python AI service
- Chunking
- Embeddings
- Vector database
- Retrieval/reranking
- Source grounding
- RAG evaluation

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
- [ ] Finish Phase 8 Codex implementation review
- [ ] Run Phase 8 local MySQL/JWT/HTTP verification
- [ ] Commit/push Phase 8
- [ ] Run V1 backend final review
- [ ] Freeze frontend-facing API contracts

### Member A — Frontend / Product Experience

Can start now; does not need to wait for Phase 8.

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

First tasks:
- [ ] Read `AGENTS.md`, this roadmap and `README.md`
- [ ] Inspect current REST API surface
- [ ] Draft V1 route/page map
- [ ] Draft shared app shell
- [ ] Define API client strategy
- [ ] Build Login/Register UI
- [ ] Build Dashboard skeleton
- [ ] Identify API contract questions for Joan

Do not:
- change backend contracts without agreement,
- add AI chat now,
- add speculative state management,
- add many dependencies without need.

### Member B — QA / DevOps / Engineering Enablement

Can start now in parallel.

Primary ownership:
- Fresh-clone verification
- Docker dev environment
- API smoke tests
- CI
- README/onboarding quality
- V1 integration acceptance checks
- Later observability/deployment

First tasks:
- [ ] Read `AGENTS.md`, this roadmap and `README.md`
- [ ] Verify project can start from a fresh clone
- [ ] Verify Docker Compose onboarding
- [ ] Review environment-variable documentation
- [ ] Build Phase 1–8 API smoke-test checklist
- [ ] Propose GitHub Actions for `mvn clean test` + build
- [ ] Identify onboarding/documentation gaps
- [ ] Prepare V1 frontend/backend acceptance checklist

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

## 10. Current parallel sprint

### Track A — Backend / Joan

Goal: close Phase 8.

1. Complete transactional Activity integration
2. Complete Activity/Dashboard tests
3. Update README
4. Run `mvn clean test`
5. Run `mvn package`
6. Run local MySQL/JWT/HTTP verification
7. Update `TEAM_STATUS.md`
8. Commit/push
9. V1 backend final review

### Track B — Frontend / Member A

Goal: prepare and start V1 frontend.

1. Read API surface
2. Define routes/pages
3. Define app shell
4. Define API client approach
5. Login/Register UI
6. Dashboard skeleton
7. Record questions/blockers in `TEAM_STATUS.md`
8. Start backend integration after contract freeze

### Track C — QA/DevOps / Member B

Goal: make the repository easy to run and verify.

1. Fresh clone verification
2. Docker Compose verification
3. Environment-variable checklist
4. API smoke-test plan
5. CI proposal / scoped implementation after review
6. V1 integration acceptance checklist
7. Record results/blockers in `TEAM_STATUS.md`

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

### Milestone 1 — Phase 8 Complete

Activity Logs + Dashboard fully integrated, tested, verified and pushed.

### Milestone 2 — V1 Backend Freeze

Review:
- API consistency
- auth/authorization
- migrations
- error model
- tests
- README
- frontend-facing contracts

After this, V1 backend scope is frozen except for bug fixes and agreed contract changes.

### Milestone 3 — V1 Full-stack Demo

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
