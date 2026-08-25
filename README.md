# OpsPilot

OpsPilot is a modular Spring Boot backend for authenticated operations workspaces, projects, tasks, documents, activity feeds, and dashboard aggregation.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop (or another Docker Compose-compatible runtime)

## Start MySQL

From the repository root, choose local development passwords and export them for Docker Compose and the backend:

```bash
export DB_NAME=opspilot
export DB_USERNAME=opspilot
export DB_PASSWORD='choose-a-local-development-password'
export MYSQL_ROOT_PASSWORD='choose-a-different-root-password'
docker compose up -d
```

Check that MySQL is ready:

```bash
docker compose ps
```

## Run the backend

Keep `DB_PASSWORD` exported from the previous step, then run:

```bash
cd backend
mvn spring-boot:run
```

The application connects to `localhost:3306` by default. Override database settings with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` when necessary.

Local browser development allows `http://localhost:5173` by default. Override the
comma-separated origin allow-list with `CORS_ALLOWED_ORIGINS` when needed. The
frozen V1 frontend contract is documented in `docs/API_CONTRACT.md`.

Once running, verify the endpoint:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP"}
```

Flyway automatically applies the versioned migrations in `src/main/resources/db/migration` on startup.

## Register, log in, and get the current user

Set a strong JWT secret before running outside local development. The configured fallback is for local development only.

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

The API is stateless: use `Authorization: Bearer <accessToken>` on each protected request. Access tokens expire after `JWT_EXPIRATION_SECONDS` (one hour by default); refresh tokens are not part of this phase.

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

## Run tests

```bash
cd backend
mvn test
```

The health endpoint test uses Spring MVC's test support and does not require a running MySQL instance.

## Stop MySQL

```bash
docker compose down
```

Use `docker compose down -v` only when you explicitly want to delete the local MySQL data volume.
