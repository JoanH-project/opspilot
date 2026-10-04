# OpsPilot Frontend

This frontend implements the V1 authentication flow, workspace bootstrap, workspace switching, and the live workspace Dashboard.

After sign-in, the app loads the member's workspaces:

- No workspaces: create the first workspace.
- One workspace: open it automatically.
- Multiple workspaces: restore the last valid selection or ask the user to choose.

The selected workspace is encoded in the URL at `/app/:workspaceId/dashboard`; its last valid ID is kept in `localStorage`. The Dashboard reads live counts and recent activity from the backend. Project, task, and document management remain later frontend phases.

## Local development

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
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
