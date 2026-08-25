# OpsPilot V1 API Contract

This document is the frozen frontend/backend contract for OpsPilot V1. Contract changes after this point require an agreed bug fix or explicit API review.

## Conventions

- Local API base URL: `http://localhost:8080`
- Frontend configuration: `VITE_API_BASE_URL=http://localhost:8080`
- JSON property names use camelCase.
- IDs are JSON numbers backed by Java `Long`; V1 TypeScript clients may use `number`.
- `Instant` values are ISO-8601 timestamps, for example `2026-08-25T12:41:43.222692Z`.
- `LocalDate` values use `YYYY-MM-DD`.
- Nullable response fields are explicitly identified below. Lists are currently unpaginated except the bounded activity feed.

## Authentication

`POST /api/auth/register`, `POST /api/auth/login`, and `GET /api/health` are public. Every other endpoint requires:

```http
Authorization: Bearer <accessToken>
```

The API is stateless. Logout is a frontend operation that removes the access token; V1 has no backend logout or refresh-token endpoint.

`LoginResponse`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "User"
  }
}
```

`expiresIn` is measured in seconds. `GET /api/users/me` restores the authenticated user after an application reload.

## Error contract

Application and authentication errors use the same shape:

```json
{
  "timestamp": "2026-08-25T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": {
    "email": "Email must be valid"
  }
}
```

`fieldErrors` is an object and is empty for non-field errors. A frontend can use one typed error handler for:

- `400`: validation, malformed JSON, invalid enum/query input
- `401`: missing, invalid, or expired JWT; invalid login credentials
- `403`: authenticated member lacks permission for the operation
- `404`: missing resource or workspace hidden from a non-member
- `409`: duplicate email or modification of an archived resource
- `500`: unexpected server error

## Endpoint inventory

Unless marked public, every endpoint requires JWT authentication. Successful JSON responses use `application/json`.

### Health

| Method | Route | Auth | Request / query | Response | Important statuses |
|---|---|---|---|---|---|
| GET | `/api/health` | Public | — | `{ "status": "UP" }` | 200 |

### Auth

| Method | Route | Auth | Request | Response | Important statuses |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | Public | `RegisterRequest` | `UserResponse` | 201, 400, 409 |
| POST | `/api/auth/login` | Public | `LoginRequest` | `LoginResponse` | 200, 400, 401 |

- `RegisterRequest`: `email: string` (required, valid, max 320), `password: string` (8–72), `name: string` (required, max 100).
- `LoginRequest`: `email: string`, `password: string`; both required.

### User

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| GET | `/api/users/me` | — | `UserResponse` | 200, 401 |

`UserResponse`: `id`, `email`, `name`, `createdAt`. Password fields are never returned.

### Workspaces

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| POST | `/api/workspaces` | `CreateWorkspaceRequest` | `WorkspaceResponse` | 201, 400, 401 |
| GET | `/api/workspaces` | — | `WorkspaceSummaryResponse[]` | 200, 401 |
| GET | `/api/workspaces/{workspaceId}` | — | `WorkspaceResponse` | 200, 401, 404 |
| PATCH | `/api/workspaces/{workspaceId}` | `UpdateWorkspaceRequest` | `WorkspaceResponse` | 200, 400, 401, 403, 404 |

- Create/update request: `name: string` (required, non-blank, max 100).
- `WorkspaceSummaryResponse`: `id`, `name`, `role`, `createdAt`.
- `WorkspaceResponse`: `id`, `name`, `owner`, current user's `role`, `createdAt`, `updatedAt`.
- `owner`: `{ id, email, name }`.
- Only `OWNER` and `ADMIN` may rename a workspace.

### Workspace members

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| GET | `/api/workspaces/{workspaceId}/members` | — | `WorkspaceMemberResponse[]` | 200, 401, 404 |

`WorkspaceMemberResponse`: `user: { id, email, name }`, `role`, `joinedAt`. This list supplies task-assignee choices.

### Projects

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| POST | `/api/workspaces/{workspaceId}/projects` | `CreateProjectRequest` | `ProjectResponse` | 201, 400, 401, 404 |
| GET | `/api/workspaces/{workspaceId}/projects` | `status` (default `ACTIVE`) | `ProjectResponse[]` | 200, 400, 401, 404 |
| GET | `/api/projects/{projectId}` | — | `ProjectResponse` | 200, 401, 404 |
| PATCH | `/api/projects/{projectId}` | `UpdateProjectRequest` | `ProjectResponse` | 200, 400, 401, 403, 404, 409 |
| POST | `/api/projects/{projectId}/archive` | — | `ProjectResponse` | 200, 401, 403, 404 |

- Create: `name: string` (required, max 150), `description?: string | null` (max 5000).
- Update: `name?: string | null`, `description?: string | null`; omitted/null fields are unchanged. Send an empty description to clear its display text.
- `ProjectResponse`: `id`, `workspaceId`, `name`, `description: string | null`, `status`, `creator: { id, email, name }`, `createdAt`, `updatedAt`.
- Create is available to members. Update/archive requires `OWNER`, `ADMIN`, or the project creator.
- Archiving is idempotent. Archived projects remain readable but cannot be edited or accept task writes.

### Tasks

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| POST | `/api/projects/{projectId}/tasks` | `CreateTaskRequest` | `TaskResponse` | 201, 400, 401, 404, 409 |
| GET | `/api/projects/{projectId}/tasks` | optional `status`, `priority`, `assigneeId` | `TaskResponse[]` | 200, 400, 401, 404 |
| GET | `/api/tasks/{taskId}` | — | `TaskResponse` | 200, 401, 404 |
| PATCH | `/api/tasks/{taskId}` | `UpdateTaskRequest` | `TaskResponse` | 200, 400, 401, 404, 409 |
| PATCH | `/api/tasks/{taskId}/status` | `UpdateTaskStatusRequest` | `TaskResponse` | 200, 400, 401, 404, 409 |

- Create: `title: string` (required, max 200), optional `description` (max 5000), `priority`, `assigneeId`, and `dueDate`.
- Omitted priority defaults to `MEDIUM`; initial status is `TODO`.
- Metadata update fields: optional `title`, `description`, `priority`, `assigneeId`, `dueDate`, plus `clearAssignee: boolean` and `clearDueDate: boolean`.
- Status update: `{ "status": "IN_PROGRESS" }`.
- `TaskResponse`: `id`, `projectId`, `title`, `description: string | null`, `status`, `priority`, `assignee: TaskUserResponse | null`, `creator`, `dueDate: string | null`, `createdAt`, `updatedAt`.
- `TaskUserResponse`: `{ id, email, name }`.
- Assignees must be members of the task's workspace. Any workspace member may create or update tasks in an active project.

### Documents

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| POST | `/api/workspaces/{workspaceId}/documents` | `CreateDocumentRequest` | `DocumentResponse` | 201, 400, 401, 404 |
| GET | `/api/workspaces/{workspaceId}/documents` | `status` (default `ACTIVE`) | `DocumentSummaryResponse[]` | 200, 400, 401, 404 |
| GET | `/api/documents/{documentId}` | — | `DocumentResponse` | 200, 401, 404 |
| PATCH | `/api/documents/{documentId}` | `UpdateDocumentRequest` | `DocumentResponse` | 200, 400, 401, 403, 404, 409 |
| POST | `/api/documents/{documentId}/archive` | — | `DocumentResponse` | 200, 401, 403, 404 |

- Create: `title: string` (required, max 200), `content: string` (required, max 1,000,000).
- Update: optional `title` and `content` with the same limits.
- `DocumentSummaryResponse`: `id`, `title`, `status`, `creator: { id, name }`, `createdAt`, `updatedAt`. It intentionally excludes LONGTEXT content.
- `DocumentResponse`: summary fields plus `workspaceId` and full `content`.
- Create is available to members. Update/archive requires `OWNER`, `ADMIN`, or the document creator.
- Archiving is idempotent. Archived documents remain readable but cannot be edited.

### Activity

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| GET | `/api/workspaces/{workspaceId}/activities` | `limit` (default 20, range 1–100) | `ActivityResponse[]` | 200, 400, 401, 404 |

`ActivityResponse`: `id`, `type`, `entityType`, `entityId`, `message`, `actor: { id, name }`, `createdAt`. Results are newest first and limited to the requested workspace.

### Dashboard

| Method | Route | Request / query | Response | Important statuses |
|---|---|---|---|---|
| GET | `/api/workspaces/{workspaceId}/dashboard` | — | `WorkspaceDashboardResponse` | 200, 401, 404 |

`WorkspaceDashboardResponse`:

- `workspaceId`
- `projects: { active, archived }`
- `tasks: { total, todo, inProgress, done, overdue }`
- `documents: { active, archived }`
- `recentActivities: ActivityResponse[]` (up to 20, newest first)

A task is overdue when `dueDate < today` and status is not `DONE`.

## Enums

- `WorkspaceRole`: `OWNER | ADMIN | MEMBER`
- `ProjectStatus`: `ACTIVE | ARCHIVED`
- `TaskStatus`: `TODO | IN_PROGRESS | DONE`
- `TaskPriority`: `LOW | MEDIUM | HIGH`
- `DocumentStatus`: `ACTIVE | ARCHIVED`
- `ActivityType`: `WORKSPACE_CREATED | PROJECT_CREATED | PROJECT_UPDATED | PROJECT_ARCHIVED | TASK_CREATED | TASK_UPDATED | TASK_STATUS_CHANGED | DOCUMENT_CREATED | DOCUMENT_UPDATED | DOCUMENT_ARCHIVED`

Enums are serialized as the uppercase strings shown above.

## Frontend bootstrap flow

1. Register or log in and store the bearer token.
2. Call `GET /api/users/me` to restore current-user state.
3. Call `GET /api/workspaces`.
4. If the list is empty, create the first workspace with `POST /api/workspaces`.
5. Select a workspace ID and call `GET /api/workspaces/{workspaceId}/dashboard`.
6. Load projects, tasks, documents, members, or the full activity feed as their screens require.

Authorization is always enforced by backend services. The frontend may hide unavailable actions for usability, but must not be treated as a security boundary. A non-member generally receives `404` so workspace existence is not disclosed.

## Browser development and CORS

The backend allows `http://localhost:5173` by default for local Vite development. Override the exact allow-list with a comma-separated environment variable:

```bash
export CORS_ALLOWED_ORIGINS='http://localhost:5173'
```

The CORS policy permits the methods used by V1 (`GET`, `POST`, `PATCH`, `OPTIONS`) and the `Authorization`, `Content-Type`, and `Accept` request headers. Credentials are disabled because authentication uses an explicit bearer header, not cookies.

## V1 scale decisions

Workspace, member, project, task, and document lists are intentionally unpaginated for the V1/demo scope. Activity is bounded to 100 records per request. Pagination, server-side project/task search, and larger-scale list virtualization are deferred until there is a demonstrated production need.
