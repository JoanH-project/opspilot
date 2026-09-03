# OpsPilot

OpsPilot is a full-stack operations platform: a Spring Boot backend with JWT authentication, workspace RBAC, and a React + TypeScript frontend (Vite).

```text
MySQL (:3306)
      ↓
Spring Boot (:8080)
      ↓
React / Vite (:5173)
```

## Prerequisites

- **Java 21** — backend target (`backend/pom.xml`). A newer locally installed JDK is fine; Maven compiles with `--release 21`.
- **Maven 3.9+**
- **Docker Desktop** (or another Docker Compose-compatible runtime) for local MySQL
- **Node.js 20+** and **npm** for the frontend

## Quick start (fresh clone)

```bash
git clone https://github.com/JoanH-project/opspilot.git
cd opspilot
cp .env.example .env          # edit passwords locally; do not commit .env
docker compose up -d
docker compose ps             # wait until mysql is healthy
```

### 1. Start the backend

Export the database password, then run Spring Boot:

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

### 2. Start the frontend

In a second terminal:

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

Open `http://localhost:5173` in your browser.

The frontend expects:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

See `frontend/README.md` for frontend-specific development notes.

## Environment configuration

See `docs/ENVIRONMENT.md` for the full variable reference.

Minimum for local full-stack development:

| Variable | Required | Notes |
|---|---|---|
| `DB_PASSWORD` | Yes | Must match Docker Compose and backend |
| `MYSQL_ROOT_PASSWORD` | Yes | Required by Docker Compose only |
| `VITE_API_BASE_URL` | Recommended | Defaults to `http://localhost:8080` in frontend code |
| `JWT_SECRET` | Recommended outside local dev | Backend has a development-only fallback |

Copy the repository root `.env.example` to `.env` for Docker Compose. Copy `frontend/.env.example` to `frontend/.env` for the Vite app. Export `DB_PASSWORD` in your shell (or IDE run config) before starting the backend.

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

Backend (`backend/`):

```bash
mvn clean test     # 38 automated tests; no running MySQL required
mvn package        # builds the runnable JAR after tests pass
```

Backend CI uses `mvn clean verify`.

Frontend (`frontend/`):

```bash
npm ci
npm run lint
npm run build
```

## Manual verification

| Checklist | Purpose |
|---|---|
| `docs/API_SMOKE_TEST.md` | Backend HTTP/JWT end-to-end verification |
| `docs/FRONTEND_SMOKE_TEST.md` | Frontend F1 browser verification (Register, Login, ProtectedRoute, CORS, etc.) |

## Documentation map

| Document | Purpose |
|---|---|
| `docs/API_CONTRACT.md` | Frozen V1 frontend/backend API contract |
| `docs/API_SMOKE_TEST.md` | Human backend verification checklist |
| `docs/FRONTEND_SMOKE_TEST.md` | Human frontend F1 verification checklist |
| `docs/ENVIRONMENT.md` | Environment variable reference |
| `frontend/README.md` | Frontend-specific setup and development |
| `AGENTS.md` | Long-lived engineering rules |
| `PROJECT_ROADMAP.md` | Architecture, milestones, team ownership |

Frontend developers must implement against `docs/API_CONTRACT.md` and must not guess or change backend contracts without agreement.

## Stop MySQL

```bash
docker compose down
```

Use `docker compose down -v` only when you explicitly want to delete the local MySQL data volume.
