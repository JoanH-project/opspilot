# OpsPilot Frontend

This frontend implements V1 authentication, workspace bootstrap and management, a live workspace Dashboard, project and task management, and workspace documents. TanStack Query owns server state; AuthContext remains the sole owner of authenticated identity and token.

After sign-in, the app loads the member's workspaces:

- No workspaces: create the first workspace.
- One workspace: open it automatically.
- Multiple workspaces: restore the last valid selection or ask the user to choose.

The selected workspace is encoded in the URL at `/app/:workspaceId/dashboard`; its last valid ID is stored per authenticated user. Workspace details and the read-only members list are available from the app shell. Owners and admins can rename their workspace. A Dashboard 404 remains in a recoverable state instead of redirecting repeatedly to the same workspace.

The Dashboard reads live counts and recent activity from the backend. Projects and documents are workspace-scoped; tasks are managed inside their project. Archived projects and documents are read-only. Document preview renders Markdown with raw HTML disabled.

Main workspace routes:

- `/app/:workspaceId/dashboard`
- `/app/:workspaceId/projects`
- `/app/:workspaceId/projects/:projectId`
- `/app/:workspaceId/documents`
- `/app/:workspaceId/documents/:documentId`

## Local development

```bash
cd frontend
npm ci
cp .env.example .env
npm run dev
```

Run the workspace behavior tests, lint, and production build with:

```bash
npm test
npm run lint
npm run build
```

The local Vite app expects the backend at:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## Environment

Create a frontend environment file with the value below:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

This is intentionally kept out of source control and should not include secrets.
