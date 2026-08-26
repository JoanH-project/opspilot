# OpsPilot

OpsPilot is a modular Spring Boot backend for authenticated operations workspaces, projects, tasks, documents, activity feeds, and dashboard aggregation.

## Prerequisites

- **Java 21** — the backend targets Java 21 (`backend/pom.xml`). A newer locally installed JDK is fine; Maven compiles with `--release 21`.
- **Maven 3.9+**
- **Docker Desktop** (or another Docker Compose-compatible runtime) for local MySQL

No Node.js setup is required for the current backend-only repository.

## Quick start (fresh clone)

```bash
git clone https://github.com/JoanH-project/opspilot.git
cd opspilot
cp .env.example .env          # edit passwords locally; do not commit .env
docker compose up -d
docker compose ps             # wait until mysql is healthy
```

Export the same database password for the backend, then start it:

```bash
# macOS / Linux
export DB_PASSWORD='your-local-password-from-.env'
cd backend
mvn spring-boot:run
```

**Windows PowerShell:**

```powershell
$env:DB_PASSWORD = 'your-local-password-from-.env'
cd backend
mvn spring-boot:run
```

Verify health:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP"}
```

Flyway automatically applies the versioned migrations in `backend/src/main/resources/db/migration` on startup.

## Environment configuration

See `docs/ENVIRONMENT.md` for the full variable reference.

Minimum for local MySQL + backend:

| Variable | Required | Notes |
|---|---|---|
| `DB_PASSWORD` | Yes | Must match Docker Compose and backend |
| `MYSQL_ROOT_PASSWORD` | Yes | Required by Docker Compose only |
| `JWT_SECRET` | Recommended outside local dev | Backend has a development-only fallback |

Copy `.env.example` to `.env` for Docker Compose. Export `DB_PASSWORD` in your shell (or IDE run config) before starting the backend.

## Register, log in, and get the current user

For non-local deployments, set a strong JWT secret:

```bash
export JWT_SECRET='replace-with-a-long-random-secret-at-least-32-characters'
export JWT_EXPIRATION_SECONDS=3600
```

Register a user:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"joan@example.com","password":"password123","name":"Joan"}'
```

Log in and copy the returned `accessToken`:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"joan@example.com","password":"password123"}'
```

Request the current user with the token:

```bash
curl http://localhost:8080/api/users/me \
  -H 'Authorization: Bearer <accessToken>'
```

The API is stateless: use `Authorization: Bearer <accessToken>` on each protected request. Access tokens expire after `JWT_EXPIRATION_SECONDS` (one hour by default); refresh tokens are not part of V1.

## Workspaces

All workspace endpoints require an access token:

```bash
export ACCESS_TOKEN='<accessToken>'
```

```bash
# Create
curl -X POST http://localhost:8080/api/workspaces \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Joan Workspace"}'

# List workspaces available to the current user
curl http://localhost:8080/api/workspaces \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Get one workspace
curl http://localhost:8080/api/workspaces/1 \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Rename (OWNER or ADMIN only)
curl -X PATCH http://localhost:8080/api/workspaces/1 \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Updated Workspace"}'

# List members
curl http://localhost:8080/api/workspaces/1/members \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Projects

Use the access token from login for all project operations:

```bash
export ACCESS_TOKEN='<accessToken>'
```

```bash
# Create a project
curl -X POST http://localhost:8080/api/workspaces/1/projects \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Customer Portal","description":"Customer-facing portal"}'

# List active projects (default)
curl http://localhost:8080/api/workspaces/1/projects \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# List archived projects
curl 'http://localhost:8080/api/workspaces/1/projects?status=ARCHIVED' \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Get, update, or archive a project
curl http://localhost:8080/api/projects/1 -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X PATCH http://localhost:8080/api/projects/1 -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d '{"name":"Updated Portal"}'
curl -X POST http://localhost:8080/api/projects/1/archive -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Tasks

```bash
# Create, or create unassigned by omitting assigneeId
curl -X POST http://localhost:8080/api/projects/1/tasks -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d '{"title":"Fix login bug","priority":"HIGH","assigneeId":2,"dueDate":"2026-08-25"}'
# List and filter
curl http://localhost:8080/api/projects/1/tasks -H "Authorization: Bearer $ACCESS_TOKEN"
curl 'http://localhost:8080/api/projects/1/tasks?status=TODO&priority=HIGH' -H "Authorization: Bearer $ACCESS_TOKEN"
# Get, update, unassign, and change status
curl http://localhost:8080/api/tasks/1 -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X PATCH http://localhost:8080/api/tasks/1 -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d '{"title":"Updated task","clearAssignee":true,"clearDueDate":true}'
curl -X PATCH http://localhost:8080/api/tasks/1/status -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}'
```

## Documents

```bash
curl -X POST http://localhost:8080/api/workspaces/1/documents -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d '{"title":"Deployment Runbook","content":"# Deployment\n\nSteps"}'
curl http://localhost:8080/api/workspaces/1/documents -H "Authorization: Bearer $ACCESS_TOKEN"
curl http://localhost:8080/api/documents/1 -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X PATCH http://localhost:8080/api/documents/1 -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' -d '{"content":"Updated Markdown"}'
curl -X POST http://localhost:8080/api/documents/1/archive -H "Authorization: Bearer $ACCESS_TOKEN"
curl 'http://localhost:8080/api/workspaces/1/documents?status=ARCHIVED' -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Activity feed and dashboard

```bash
# Newest workspace activities first (default limit: 20)
curl http://localhost:8080/api/workspaces/1/activities \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Request a smaller activity page (valid range: 1–100)
curl 'http://localhost:8080/api/workspaces/1/activities?limit=10' \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Workspace project/task/document counts plus the 20 most recent activities
curl http://localhost:8080/api/workspaces/1/dashboard \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Activity logs are immutable and are written in the same database transaction as the
corresponding workspace, project, task, or document change. Dashboard totals use
database aggregate queries. A task is overdue when its due date is before today and
its status is not `DONE`.

## Tests and build

From `backend/`:

```bash
mvn clean test     # 38 automated tests; no running MySQL required
mvn package        # builds the runnable JAR after tests pass
```

CI uses `mvn clean verify`, which runs tests and packages in one Maven lifecycle.

## Documentation map

| Document | Purpose |
|---|---|
| `docs/API_CONTRACT.md` | Frozen V1 frontend/backend API contract |
| `docs/API_SMOKE_TEST.md` | Human end-to-end backend verification checklist |
| `docs/ENVIRONMENT.md` | Environment variable reference |
| `AGENTS.md` | Long-lived engineering rules |
| `PROJECT_ROADMAP.md` | Architecture, milestones, team ownership |

Frontend developers must implement against `docs/API_CONTRACT.md` and must not guess or change backend contracts without agreement.

## Stop MySQL

```bash
docker compose down
```

Use `docker compose down -v` only when you explicitly want to delete the local MySQL data volume.
