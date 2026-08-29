# OpsPilot Frontend

This frontend is the V1 React + TypeScript foundation for OpsPilot. It includes authentication, protected routing, and a minimal app shell ready for later dashboard and workspace flows.

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
