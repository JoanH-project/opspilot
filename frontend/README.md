# OpsPilot Frontend

This frontend implements the V1 authentication flow, workspace bootstrap and management, workspace switching, and the live workspace Dashboard. TanStack Query owns workspace and Dashboard server state; AuthContext remains the sole owner of the authenticated identity and token.

After sign-in, the app loads the member's workspaces:

- No workspaces: create the first workspace.
- One workspace: open it automatically.
- Multiple workspaces: restore the last valid selection or ask the user to choose.

The selected workspace is encoded in the URL at `/app/:workspaceId/dashboard`; its last valid ID is stored per authenticated user. Workspace details and the read-only members list are available from the app shell. Owners and admins can rename their workspace. A Dashboard 404 remains in a recoverable state instead of redirecting repeatedly to the same workspace.

The Dashboard reads live counts and recent activity from the backend. Project, task, and document management remain later frontend phases.

## Local development

```bash
cd frontend
npm install
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
