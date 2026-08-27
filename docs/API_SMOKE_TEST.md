# OpsPilot V1 API Smoke Test

Human end-to-end verification checklist for the frozen V1 backend. Use this after a fresh clone, local MySQL startup, or before handing off a backend change for review.

**Contract source:** `docs/API_CONTRACT.md`

**Base URL:** `http://localhost:8080`

## Before you start

1. Start MySQL with Docker Compose (see `README.md`).
2. Export `DB_PASSWORD` (and other variables if needed). See `docs/ENVIRONMENT.md`.
3. Start the backend: `cd backend && mvn spring-boot:run`.
4. Confirm Flyway applied migrations V1–V7 on startup (check backend logs).
5. Use a fresh token for each full run. Do not reuse an old JWT.

## ID capture rules

Do **not** assume fixed IDs such as `workspaceId = 1`. Capture IDs from each successful response and reuse them in later steps:

| Variable | Capture from |
|---|---|
| `ACCESS_TOKEN` | `POST /api/auth/login` → `accessToken` |
| `USER_ID` | `GET /api/users/me` → `id` |
| `WORKSPACE_ID` | `POST /api/workspaces` → `id` |
| `PROJECT_ID` | `POST /api/workspaces/{workspaceId}/projects` → `id` |
| `TASK_ID` | `POST /api/projects/{projectId}/tasks` → `id` |
| `DOCUMENT_ID` | `POST /api/workspaces/{workspaceId}/documents` → `id` |

Use a unique email per run, for example `smoke-$(date +%s)@example.com`, to avoid duplicate-email conflicts.

---

## Happy-path flow

### 1. Health

| Item | Value |
|---|---|
| Endpoint | `GET /api/health` |
| Auth | None |
| Expected status | `200` |
| Expected body | `{ "status": "UP" }` |

```bash
curl -s http://localhost:8080/api/health
```

---

### 2. Register

| Item | Value |
|---|---|
| Endpoint | `POST /api/auth/register` |
| Auth | None |
| Expected status | `201` |
| Capture | none required yet |

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"smoke-user@example.com","password":"password123","name":"Smoke User"}'
```

Expect `id`, `email`, `name`, `createdAt`. No password fields.

---

### 3. Login

| Item | Value |
|---|---|
| Endpoint | `POST /api/auth/login` |
| Auth | None |
| Expected status | `200` |
| Capture | `accessToken` → `ACCESS_TOKEN` |

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"smoke-user@example.com","password":"password123"}'
```

Expect `accessToken`, `tokenType: "Bearer"`, `expiresIn`, and nested `user`.

---

### 4. Current user

| Item | Value |
|---|---|
| Endpoint | `GET /api/users/me` |
| Auth | `Authorization: Bearer $ACCESS_TOKEN` |
| Expected status | `200` |
| Capture | `id` → `USER_ID` |

```bash
curl -s http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

### 5. Create workspace

| Item | Value |
|---|---|
| Endpoint | `POST /api/workspaces` |
| Auth | Bearer token |
| Expected status | `201` |
| Capture | `id` → `WORKSPACE_ID` |

```bash
curl -s -X POST http://localhost:8080/api/workspaces \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Smoke Workspace"}'
```

Expect `role: "OWNER"`.

---

### 6. List workspaces

| Item | Value |
|---|---|
| Endpoint | `GET /api/workspaces` |
| Auth | Bearer token |
| Expected status | `200` |

```bash
curl -s http://localhost:8080/api/workspaces \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Expect an array containing the workspace created above.

---

### 7. Create project

| Item | Value |
|---|---|
| Endpoint | `POST /api/workspaces/{workspaceId}/projects` |
| Auth | Bearer token |
| Expected status | `201` |
| Capture | `id` → `PROJECT_ID` |

```bash
curl -s -X POST "http://localhost:8080/api/workspaces/$WORKSPACE_ID/projects" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Smoke Project","description":"Smoke test project"}'
```

Expect `status: "ACTIVE"`.

---

### 8. Create task

| Item | Value |
|---|---|
| Endpoint | `POST /api/projects/{projectId}/tasks` |
| Auth | Bearer token |
| Expected status | `201` |
| Capture | `id` → `TASK_ID` |

```bash
curl -s -X POST "http://localhost:8080/api/projects/$PROJECT_ID/tasks" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke task\",\"priority\":\"HIGH\",\"assigneeId\":$USER_ID,\"dueDate\":\"2026-08-20\"}"
```

Expect `status: "TODO"`. The due date is intentionally in the past so dashboard overdue counts can be checked later.

---

### 9. Update task status

| Item | Value |
|---|---|
| Endpoint | `PATCH /api/tasks/{taskId}/status` |
| Auth | Bearer token |
| Expected status | `200` |

```bash
curl -s -X PATCH "http://localhost:8080/api/tasks/$TASK_ID/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}'
```

---

### 10. Create document

| Item | Value |
|---|---|
| Endpoint | `POST /api/workspaces/{workspaceId}/documents` |
| Auth | Bearer token |
| Expected status | `201` |
| Capture | `id` → `DOCUMENT_ID` |

```bash
curl -s -X POST "http://localhost:8080/api/workspaces/$WORKSPACE_ID/documents" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Smoke Runbook","content":"# Smoke\n\nVerification doc"}'
```

---

### 11. Activity feed

| Item | Value |
|---|---|
| Endpoint | `GET /api/workspaces/{workspaceId}/activities` |
| Auth | Bearer token |
| Expected status | `200` |

```bash
curl -s "http://localhost:8080/api/workspaces/$WORKSPACE_ID/activities" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Check:**

- Array is newest first (`createdAt` descending).
- Entries exist for workspace/project/task/document actions from this run.
- Each item includes `actor.id`, `actor.name`, `type`, `entityType`, `entityId`, and `message`.
- Actors are safe user summaries (no password fields).

**Limit behavior:**

```bash
curl -s "http://localhost:8080/api/workspaces/$WORKSPACE_ID/activities?limit=3" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Expect at most 3 items.

---

### 12. Dashboard

| Item | Value |
|---|---|
| Endpoint | `GET /api/workspaces/{workspaceId}/dashboard` |
| Auth | Bearer token |
| Expected status | `200` |

```bash
curl -s "http://localhost:8080/api/workspaces/$WORKSPACE_ID/dashboard" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Check:**

- `workspaceId` matches `WORKSPACE_ID`.
- `projects.active >= 1`, `projects.archived >= 0`.
- `tasks.total >= 1`, with realistic `todo` / `inProgress` / `done` counts after the status change.
- `tasks.overdue >= 1` because the task due date is before today and status is not `DONE`.
- `documents.active >= 1`.
- `recentActivities` is an array (up to 20 items), newest first.

---

### 13. Archive idempotency

Archive the project twice. Both calls should succeed and the project should remain archived without misleading duplicate side effects in the activity feed.

```bash
curl -s -X POST "http://localhost:8080/api/projects/$PROJECT_ID/archive" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -s -X POST "http://localhost:8080/api/projects/$PROJECT_ID/archive" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Expect `200` and `status: "ARCHIVED"` on both responses.

Re-check activity feed: there should not be duplicate archive noise for the second idempotent call.

---

## Negative smoke checks

### Invalid login

| Item | Value |
|---|---|
| Endpoint | `POST /api/auth/login` |
| Auth | None |
| Expected status | `401` |

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"smoke-user@example.com","password":"wrong-password"}'
```

---

### Unauthenticated protected request

| Item | Value |
|---|---|
| Endpoint | `GET /api/users/me` |
| Auth | None |
| Expected status | `401` |

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/users/me
```

---

### Non-member workspace access

Register a second user and attempt to read the first user's workspace.

1. Register/login as `other-user@example.com`.
2. Call `GET /api/workspaces/$WORKSPACE_ID` with the second user's token.

Expected status: `404` (workspace hidden from non-members).

---

### Invalid activity limit

| Item | Value |
|---|---|
| Endpoint | `GET /api/workspaces/{workspaceId}/activities?limit=0` |
| Auth | Bearer token |
| Expected status | `400` |

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/workspaces/$WORKSPACE_ID/activities?limit=0" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Also verify `limit=101` returns `400`.

---

### Unauthorized role behavior

As the workspace `OWNER`, rename succeeds:

```bash
curl -s -X PATCH "http://localhost:8080/api/workspaces/$WORKSPACE_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Renamed Smoke Workspace"}'
```

Expected status: `200`.

If a second non-admin member is available in the workspace, the same rename with that member's token should return `403`.

---

## Completion checklist

- [ ] Health returns `UP`
- [ ] Register + login + `/users/me` work
- [ ] Workspace create/list work with captured IDs
- [ ] Project, task, and document CRUD paths exercised
- [ ] Activity feed is newest-first with safe actor data
- [ ] Dashboard counts and recent activities look correct
- [ ] Archive idempotency behaves correctly
- [ ] Negative auth/authorization/limit checks pass

When this checklist passes against a fresh local run, the V1 backend is ready for frontend integration or review handoff.
