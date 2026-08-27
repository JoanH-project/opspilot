# OpsPilot V1 Frontend Integration Plan

> Status: planning baseline for implementation after Frontend F1 approval
> Backend contract: `docs/API_CONTRACT.md`
> Scope: Login/Register, workspace bootstrap, Dashboard, Projects, Tasks, Documents, Recent Activity, and Logout

## 1. Scope and implementation principles

The V1 frontend should turn the frozen backend into one coherent, demonstrable product. It should use the backend as the authority for authentication, authorization, validation, and data. It must not add APIs, fake data, speculative state infrastructure, AI features, invitations, file uploads, collaborative editing, or pagination.

This plan assumes Frontend F1 provides an approved React + TypeScript foundation with routing, a typed API client, TanStack Query, form handling, and the Login/Register shell. No `frontend/` directory exists on `main` at the time of this review, so F1 remains a prerequisite rather than an implementation inspected here.

Key decisions:

- Put `workspaceId` in every workspace-scoped application URL.
- Treat the URL as the selected-workspace source of truth; retain only the last valid workspace ID as a local preference.
- Use the Dashboard response directly for counts and recent activity. Do not fetch duplicate activity data on initial Dashboard load.
- Keep tasks inside Project Detail because V1 has no workspace-wide task-list endpoint.
- Use a filterable task list, not a Kanban board.
- Use a Markdown textarea with an explicit Preview tab, not a rich or collaborative editor.
- Keep remote data in TanStack Query; use component/form state and URL search parameters for the small amount of client state.
- Refetch after successful mutations rather than adding optimistic-update complexity.

## 2. End-to-end user flow

```text
Unauthenticated
  -> /login or /register
  -> authenticated session bootstrap (GET /api/users/me)
  -> workspace bootstrap (GET /api/workspaces)
       -> zero: create first workspace
       -> one: select it automatically
       -> multiple: restore last valid choice or show selector
  -> /app/:workspaceId/dashboard
  -> Projects -> Project Detail -> Tasks
  -> Documents -> Document Detail/Edit
  -> Logout -> /login
```

### Authentication bootstrap

1. Register with `POST /api/auth/register`. Registration does not issue a token, so show success and send the user to Login, optionally carrying the normalized email as navigation state.
2. Log in with `POST /api/auth/login`; store the bearer token and its expiry metadata.
3. On reload, if a token exists, call `GET /api/users/me` before rendering protected content.
4. If restoration returns `401`, remove the token, clear user-scoped query data, preserve the intended URL, and redirect to `/login`.
5. Logout is local only: remove the token and last-workspace preference, clear the query cache, and replace the current history entry with `/login`.

For V1, local storage is the simplest way to survive refresh with a stateless bearer-token backend. The API client must be the only code that reads and attaches the token. This choice requires the normal XSS discipline: do not inject untrusted HTML and do not log tokens.

### Workspace bootstrap

- **Zero workspaces:** `/app` shows a focused first-workspace form. After `POST /api/workspaces`, cache the returned workspace, store its ID as the last choice, and replace the route with its Dashboard URL.
- **One workspace:** select it automatically and replace `/app` with its Dashboard URL.
- **Multiple workspaces:** if the stored ID still appears in `GET /api/workspaces`, restore it. Otherwise show a workspace selector; do not silently choose an arbitrary workspace.
- **Direct workspace URL:** verify membership through workspace-scoped data. A `404` means unavailable or not found; remove an invalid stored preference, refresh the workspace list, and return to `/app`.
- **Switching workspace:** Dashboard stays on Dashboard. Collection pages stay on the equivalent collection. Project or document detail falls back to the equivalent collection because resource IDs are not portable between workspaces.

Loading and empty states are page states, not toast-only messages. Each page should keep its title/shell visible while loading and should offer the most relevant next action when empty.

## 3. Route map

| Route | Purpose | Main API use |
|---|---|---|
| `/login` | Public login | `POST /api/auth/login` |
| `/register` | Public registration | `POST /api/auth/register` |
| `/app` | Authenticated user/workspace resolver and zero-workspace onboarding | `GET /api/users/me`, `GET/POST /api/workspaces` |
| `/app/:workspaceId/dashboard` | Workspace summary and recent activity | `GET /api/workspaces/{workspaceId}/dashboard` |
| `/app/:workspaceId/projects` | Active/archived project collection | Workspace project list/create endpoints |
| `/app/:workspaceId/projects/:projectId` | Project metadata and its task list | Project detail/update/archive and project task endpoints |
| `/app/:workspaceId/documents` | Active/archived document collection | Workspace document list/create endpoints |
| `/app/:workspaceId/documents/:documentId` | Document read/edit/preview/archive | Document detail/update/archive endpoints |

`/app` is a resolver, not a permanent landing page. Unknown public routes go to Login when unauthenticated; unknown protected routes go to the selected workspace Dashboard when one is valid, otherwise `/app`.

`workspaceId` belongs in the URL because it gives refresh-safe and shareable context, prevents hidden global selection from controlling requests, and supports multiple workspaces without duplicating page trees. The URL always wins over the stored last-workspace preference. When Project or Document Detail loads, compare its returned `workspaceId` with the route and replace a mismatched URL with the canonical one.

V1 does not need `/tasks`, `/activity`, or `/settings` routes. Tasks belong to Project Detail, recent activity belongs to Dashboard, and workspace members/rename can live in a compact workspace popover or dialog.

## 4. Application shell and navigation

The authenticated shell contains:

- OpsPilot product link to the current Dashboard.
- Workspace switcher showing the current workspace name and role.
- Primary navigation: Dashboard, Projects, Documents.
- Current-user menu with name/email and Logout.
- A workspace details action for the read-only members list and authorized rename action.

On narrow screens, the same navigation may collapse into a drawer. Route content, labels, keyboard order, and actions must remain equivalent; V1 does not need a separate mobile information architecture.

## 5. Dashboard

Use only `GET /api/workspaces/{workspaceId}/dashboard`.

### Minimum layout

1. Page header with workspace name and a short greeting or neutral summary.
2. Responsive stat-card groups:
   - Projects: Active, Archived.
   - Tasks: Total, TODO, In Progress, Done, Overdue.
   - Documents: Active, Archived.
3. Recent Activity list using `recentActivities`, newest first.

Project cards link to `/projects?status=ACTIVE|ARCHIVED`. Document cards link to `/documents?status=ACTIVE|ARCHIVED`. Task counts remain summary cards with a “Browse projects” action because the API cannot produce a correct workspace-wide filtered task screen. Do not imply that clicking Overdue can show all overdue tasks.

Activity rows show actor, backend-provided message, timestamp, and a small entity/action icon. Entity links are optional and should only be enabled where the existing route can be constructed safely. An empty feed explains that workspace changes will appear there.

Do not add charts: the contract supplies point-in-time counts, and cards communicate them more clearly with less dependency and visual overhead.

## 6. Projects and Tasks UX

### Projects List

- Default to Active projects.
- Use `status=ACTIVE|ARCHIVED` as a URL search parameter and API query input.
- Show a compact table or card list with name, description excerpt, creator, updated time, and status.
- Allow every workspace member to create a project.
- Show archived projects in a separate tab/toggle, not mixed into the active default.
- Creation uses a dialog or inline panel with name and optional description.

### Project Detail

- Show name, description, creator, status, created/updated times, and the task section.
- Show Edit and Archive only to `OWNER`, `ADMIN`, or the project creator.
- Archived projects remain readable. Replace mutation controls with a clear “Archived — read only” state.
- A `MEMBER` who is not the creator can still view the project and manage tasks while it is active, but cannot edit/archive project metadata.

### Task list decision

Use a **simple task list**. Mature tools expose both list and board layouts, while their list views make status, priority, assignee, and due-date filtering first-class ([Linear filters](https://linear.app/docs/filters), [Linear display options](https://linear.app/docs/display-options)). OpsPilot V1 already supports those filters, but has no persisted ordering or board-move contract. A Kanban interaction would add drag behavior and ordering expectations without backend support.

Recommended task list:

- Columns/rows: title, status, priority, assignee, due date, updated time.
- Filters: status, priority, assignee, represented in URL search parameters.
- Create Task dialog: title, optional description, priority (default Medium), assignee, due date.
- Metadata Edit dialog: title, description, priority, assignee, due date.
- Assignment control includes an explicit Unassigned option that sends `clearAssignee: true`.
- Due-date control includes a clear action that sends `clearDueDate: true`.
- Status can be changed through a compact select/menu using `PATCH /api/tasks/{taskId}/status`.
- Highlight overdue tasks client-side only from `dueDate < today && status !== DONE`; Dashboard remains the authoritative aggregate count.
- Disable all task writes when the parent project is archived.

Any workspace member may create and update tasks in an active project. Assignee choices come from `GET /api/workspaces/{workspaceId}/members`.

## 7. Documents UX

### Documents List

- Default to Active documents with an Active/Archived tab stored in `status` search parameters.
- Render only the summary contract: title, status, creator, created time, updated time.
- Do not fetch full content for list rows.
- Every member may create a document.

### Document Detail and editing

- Fetch `GET /api/documents/{documentId}` only when detail opens.
- Read mode renders the title, Markdown content, creator, status, and timestamps.
- Edit mode uses a title field and raw Markdown textarea with **Edit** and **Preview** tabs.
- Use one small, maintained Markdown renderer after checking F1 dependencies. Do not enable raw HTML rendering.
- Save is explicit; no autosave, collaborative editing, file upload, or split-pane requirement.
- Warn before leaving with unsaved edits.
- Show Edit and Archive only to `OWNER`, `ADMIN`, or the document creator.
- Archived documents remain readable and previewable but are read-only.

Tabbed preview is more credible than a textarea alone and simpler/responsive compared with maintaining a split editor/preview layout.

## 8. Workspace UX

- The first-workspace screen requests only the required name.
- The switcher lists workspace name and current role; selection navigates rather than mutating hidden global scope.
- A workspace details dialog loads `GET /api/workspaces/{workspaceId}` and the member list on demand.
- Members are read-only rows showing name, email, role, and joined date.
- `OWNER` and `ADMIN` see Rename; `MEMBER` does not. Rename uses the workspace detail response and updates the shell/list caches.
- Do not display invite, remove-member, or role-management controls because no such V1 endpoints exist.

## 9. Authorization and error UX

Frontend permission checks improve clarity but never replace backend authorization.

| Capability | OWNER | ADMIN | MEMBER |
|---|---:|---:|---:|
| Rename workspace | Yes | Yes | Hidden |
| Create project/document | Yes | Yes | Yes |
| Update/archive any project | Yes | Yes | Creator only |
| Update/archive any document | Yes | Yes | Creator only |
| Create/update tasks in active project | Yes | Yes | Yes |
| Read archived resources | Yes | Yes | Yes |

Use the current workspace role plus `currentUser.id === resource.creator.id` to decide whether to show resource controls. If state is stale and the backend returns `403`, keep the resource visible, close/disable the failed action, and show a permission-specific inline error or toast.

| Status | Frontend behavior |
|---|---|
| `400` | Map `fieldErrors` to fields; show `message` for malformed enum/query errors. |
| `401` | Clear token and user-scoped cache, then redirect to Login with a session-expired message. |
| `403` | Keep context visible and explain that the account lacks permission. Do not relabel it as not found. |
| `404` | Show “not found or unavailable”; for workspace failure return to `/app`, for child resource return to its collection. |
| `409` | Preserve form input, show the backend message, refetch affected data; archived-resource conflicts become read-only state. |
| `500` | Show a generic retryable page/section error without backend details. |

Loading states should use a stable skeleton or progress indicator. Empty states should distinguish “no items yet” from “filters returned no items.” Destructive archive actions require confirmation and should be disabled while pending.

## 10. Server-state strategy

TanStack Query owns API data. The router owns workspace/resource identity and filter search parameters. Local component/form state owns dialogs, drafts, and transient UI. A small auth boundary owns only token lifecycle; no Redux/Zustand-style global store is needed.

### Query keys

```ts
['currentUser']
['workspaces']
['workspace', workspaceId]
['workspaceMembers', workspaceId]
['dashboard', workspaceId]
['projects', workspaceId, { status }]
['project', projectId]
['tasks', projectId, { status, priority, assigneeId }]
['task', taskId]
['documents', workspaceId, { status }]
['document', documentId]
['activities', workspaceId, { limit }]
```

Use stable, normalized filter objects. Do not request `activities` alongside `dashboard` merely to duplicate `recentActivities`; the standalone key is for a component that explicitly uses the activities endpoint.

### Invalidation rules

| Mutation | Invalidate/update |
|---|---|
| Register | None; route to Login. |
| Login | Seed `currentUser`, then load workspaces. |
| Create workspace | `workspaces`; seed returned workspace; select it. |
| Rename workspace | `workspaces`, `workspace`, `dashboard`, `activities`. |
| Create/update/archive project | Relevant `projects`, `project`, `dashboard`, `activities`; tasks when archive changes editability. |
| Create/update/status-change task | Relevant `tasks`, `task`, `dashboard`, `activities`. |
| Create/update/archive document | Relevant `documents`, `document`, `dashboard`, `activities`. |
| Logout/401 | Clear the complete user-scoped query cache. |

Avoid broad invalidation after every action when the affected workspace/resource is known. Do not retry authorization, validation, or conflict responses. A manual Retry action is enough for initial page failures; mutation buttons must not silently replay writes.

## 11. Focused component boundaries

Reusable application components:

- `AppShell`
- `AuthGuard` / authenticated bootstrap boundary
- `WorkspaceSwitcher`
- `WorkspaceDetailsDialog`
- `PageHeader`
- `LoadingState`
- `EmptyState`
- `ApiErrorState`
- `ConfirmArchiveDialog`
- `StatusBadge`
- `PriorityBadge`
- `UserAvatar`
- `ActivityList`

Feature-specific forms and lists should stay in their feature folders: auth, workspaces, dashboard, projects/tasks, and documents. Do not build a generic CRUD framework, repository layer in the browser, or large design system.

## 12. F2 / F3 / F4 delivery sequence

### F2 — Workspace Bootstrap + Real Dashboard

**Prerequisite:** approved F1 toolchain, router/API client, auth persistence, Login/Register behavior, and protected shell foundation.

**Scope**

- Restore the current user and handle `401`.
- Zero/one/multiple workspace bootstrap.
- Workspace switcher and refresh persistence.
- Workspace details, members, and authorized rename.
- Real Dashboard cards and recent activity.
- Logout and shared loading/empty/error states used by this slice.

**Endpoints**

- `GET /api/users/me`
- `GET/POST /api/workspaces`
- `GET/PATCH /api/workspaces/{workspaceId}`
- `GET /api/workspaces/{workspaceId}/members`
- `GET /api/workspaces/{workspaceId}/dashboard`

**Acceptance criteria**

- Each workspace-count scenario reaches a deterministic Dashboard.
- Refresh restores authentication and a valid workspace URL.
- Switching workspaces never reuses previous-workspace Dashboard data.
- Dashboard shows every frozen count and up to 20 recent activities.
- Rename visibility and `403/404` handling match the contract.
- No mocked API data remains in the delivered flow.

**Excluded:** project/task/document CRUD, standalone activity page, invitations, role management, charts.

### F3 — Projects + Project Detail + Tasks

**Scope**

- Active/archived Project List and project creation.
- Project Detail, update, archive, and permission-aware controls.
- Filterable task list inside Project Detail.
- Task create, metadata update, assignment/unassignment, due-date clear, and status change.
- Archived-project read-only behavior.

**Endpoints**

- Project endpoints in the frozen contract.
- Task endpoints in the frozen contract.
- `GET /api/workspaces/{workspaceId}/members` for assignees.

**Acceptance criteria**

- Active and archived lists use the correct query status.
- Project URLs are canonical to the response workspace.
- Project controls match role/creator rules and backend denial remains safe.
- All three task filters can be combined and survive refresh in the URL.
- Create/edit/status/assign/unassign/due-date flows refetch affected tasks, Dashboard, and activity.
- Archived projects and their tasks are readable but expose no writes.

**Excluded:** Kanban, drag/drop, workspace-wide tasks, ordering, search, pagination, comments, attachments.

### F4 — Documents + V1 Polish and Integration

**Scope**

- Active/archived Document List and creation.
- Detail fetch, Markdown edit/preview, save, archive, and read-only archived state.
- Permission/error behavior consistent with Projects.
- Full V1 responsive/accessibility pass and real backend smoke verification.
- Final route guards, loading/empty/error consistency, mutation pending states, and unsaved-edit protection.

**Endpoints**

- Document endpoints in the frozen contract.
- Existing auth/workspace/dashboard endpoints for full-flow regression.

**Acceptance criteria**

- Lists never fetch full document content.
- Detail/edit/preview works for valid Markdown without rendering raw HTML.
- Role/creator action visibility and archived behavior match the backend.
- Register through Logout works against real Spring Boot/MySQL from a clean browser session.
- Type check, tests, production build, and documented V1 smoke checklist pass.

**Excluded:** collaborative editing, autosave, rich-text editing, uploads, version history, AI/RAG, pagination.

## 13. V1 frontend acceptance checklist

### Foundation and auth

- [ ] `VITE_API_BASE_URL` configures the API base URL; no URL or token is hardcoded.
- [ ] Register validates fields, handles duplicate email, and returns to Login without assuming a token.
- [ ] Login stores the bearer token, restores the user with `/me`, and never logs the token.
- [ ] Protected routes wait for auth restoration and handle expired/invalid tokens once.
- [ ] Logout clears auth, workspace preference, and user-scoped query data.

### Workspaces and navigation

- [ ] Zero, one, and multiple workspace flows match this plan.
- [ ] `workspaceId` is visible in and authoritative from the URL.
- [ ] Refresh and workspace switching do not leak cached data between workspaces.
- [ ] Members are visible without unsupported management controls.
- [ ] Rename is available only to Owner/Admin UX and backend `403` remains handled.

### Dashboard and activity

- [ ] All project/task/document counts map exactly to the Dashboard contract.
- [ ] Recent activities render newest first with meaningful empty/loading/error states.
- [ ] Dashboard does not request invented analytics or a duplicate initial activity feed.
- [ ] Count-card links only promise screens the backend can support.

### Projects and tasks

- [ ] Project create/list/view/update/archive and archived list work end to end.
- [ ] Permission-aware controls match role and creator rules.
- [ ] Task create/list/filter/update/status/assignee/due-date flows work end to end.
- [ ] Archived projects and tasks are readable but not writable.
- [ ] Task UI remains a list and does not imply persisted drag ordering.

### Documents

- [ ] Summary lists do not load full content.
- [ ] Create/detail/edit/preview/archive work end to end.
- [ ] Archived documents are readable but not editable.
- [ ] Raw HTML is not rendered from Markdown.
- [ ] Unsaved edit navigation is guarded.

### Quality and integration

- [ ] `400`, `401`, `403`, `404`, `409`, and `500` have consistent, accessible UI behavior.
- [ ] Forms display backend field errors and preserve user input after expected failures.
- [ ] Loading, empty, filtered-empty, mutation-pending, and retry states are covered.
- [ ] Keyboard navigation, labels, focus restoration, color contrast, and narrow-screen layout are checked.
- [ ] Frontend tests, type check, production build, and real backend/MySQL smoke flow pass.
- [ ] No fake implementations, future AI features, or unsupported backend actions are present.

## 14. Contract review outcome

There is no backend capability blocker for the planned V1 flow. CORS, bearer authentication, workspace bootstrap, role/creator authorization, list filters, archive behavior, Dashboard aggregation, and recent activity are all represented by the frozen contract.

One non-blocking contract drift should be resolved before F3 is approved: backend source currently declares `TaskPriority.URGENT`, while `docs/API_CONTRACT.md` freezes only `LOW | MEDIUM | HIGH`. Frontend work should implement only the frozen values and render an unknown returned value defensively; backend ownership should decide whether `URGENT` is removed from source or explicitly reviewed into the contract. This does not block F2.

Pagination, workspace-wide task browsing, invitations, and refresh tokens are intentional V1 omissions, not blockers.
