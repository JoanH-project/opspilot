# OpsPilot V1 Frontend Auth + F2 Smoke Test

Reusable manual browser checklist for authentication, workspace bootstrap, and Dashboard. Run this after MySQL, the backend, and the frontend dev server are all running locally.

**Prerequisites:** see root `README.md` and `frontend/README.md`.

**Local URLs:**
- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`

**Token storage key:** `opspilot_access_token` in `localStorage`.

Use a fresh email per run when registering, for example `f2-smoke-<timestamp>@example.com`.

For the four focused post–PR #4 auth regressions (409 / 401 / network / login-then-`/users/me` failure), use `docs/AUTH_REGRESSION.md` instead of re-running the auth checks.

---

## Before you start

1. Start MySQL: `docker compose up -d`
2. Export `DB_PASSWORD` and start backend: `cd backend && mvn spring-boot:run`
3. Configure frontend: `cd frontend && cp .env.example .env`
4. Start frontend: `npm run dev`
5. Open `http://localhost:5173` in a browser with DevTools available

---

## 1. Register (happy path)

| Step | Action | Expected |
|---|---|---|
| 1 | Open `/register` | Register form renders |
| 2 | Submit valid name, email, password | Success message appears |
| 3 | Wait for redirect | Navigates to `/login` |

Use password at least 8 characters, for example `password123`.

---

## 2. Duplicate email (409)

| Step | Action | Expected |
|---|---|---|
| 1 | Register a user successfully | Account created |
| 2 | Register again with the **same email** | Error banner shows backend message |
| 3 | Check DevTools Network tab | Response status is **409**, not `0` |

The UI should show a registration failure message, not a generic network error.

---

## 3. Login (happy path)

| Step | Action | Expected |
|---|---|---|
| 1 | Open `/login` | Login form renders |
| 2 | Sign in with the registered account | Redirects to workspace bootstrap, then `/app/{workspaceId}/dashboard` |
| 3 | Check top bar | Shows the current workspace name and user's name |
| 4 | Check `localStorage` | `opspilot_access_token` is set |

---

## 4. Current user restoration (`/users/me`)

| Step | Action | Expected |
|---|---|---|
| 1 | While logged in, confirm dashboard loads | Workspace name and live Dashboard visible |
| 2 | Hard refresh the page (`Ctrl+R` / `Cmd+R`) | Brief loading state, then the same workspace Dashboard returns |
| 3 | Check Network tab on refresh | `GET /api/users/me` returns **200** |

Session should restore from the stored token without requiring login again.

---

## 5. ProtectedRoute

| Step | Action | Expected |
|---|---|---|
| 1 | Log out | Returns to login flow |
| 2 | Manually open `/app` | Redirects to `/login` |
| 3 | Manually open `/app/{workspaceId}/dashboard` while logged out | Redirects to `/login` |
| 4 | Log in again | Can access protected routes |

---

## 6. Authenticated auth-route redirect

| Step | Action | Expected |
|---|---|---|
| 1 | While logged in, open `/login` | Redirects to `/app` and resolves the workspace |
| 2 | While logged in, open `/register` | Redirects to `/app` and resolves the workspace |

---

## 7. Invalid token handling

| Step | Action | Expected |
|---|---|---|
| 1 | Log in successfully | Workspace Dashboard visible |
| 2 | In DevTools → Application → Local Storage, change `opspilot_access_token` to `invalid-token` | Token edited |
| 3 | Hard refresh | User is treated as logged out |
| 4 | Check storage after refresh | Token removed from `localStorage` |
| 5 | Attempt `/app` | Redirects to `/login` |

---

## 8. Logout

| Step | Action | Expected |
|---|---|---|
| 1 | Log in again | Workspace Dashboard visible |
| 2 | Click **Logout** in the app shell | Returns to unauthenticated state |
| 3 | Check `localStorage` | `opspilot_access_token` and `opspilot_last_workspace_id` removed |
| 4 | Open `/app` | Redirects to `/login` |

---

## 9. Workspace bootstrap and switching

| Scenario | Action | Expected |
|---|---|---|
| Zero workspaces | Sign in with a new account | First-workspace form appears |
| Create first workspace | Submit a valid name | Workspace is created and its Dashboard opens |
| One workspace | Open `/app` | The only workspace opens automatically |
| Multiple workspaces, saved selection valid | Open `/app` | The saved workspace Dashboard opens |
| Multiple workspaces, no valid saved selection | Clear `opspilot_last_workspace_id`, then open `/app` | Workspace chooser appears; no workspace is silently selected |
| Switch workspace | Use the sidebar workspace selector | URL and displayed data change to the selected workspace |
| Invalid workspace URL | Open `/app/999999/dashboard` for an inaccessible ID | Returns to `/app` and presents valid workspace options |

Logout should remove both `opspilot_last_workspace_id` and `opspilot_access_token`.

---

## 10. Live Dashboard

| Check | Expected |
|---|---|
| Network request | `GET /api/workspaces/{workspaceId}/dashboard` returns `200` |
| Project cards | Active and archived counts match the response |
| Task cards | Total, TODO, In Progress, Done, and Overdue counts match the response |
| Document cards | Active and archived counts match the response |
| Recent activity | Backend-provided message, actor, and timestamp are visible |
| Empty feed | Explains that workspace changes will appear there |
| Switch workspace | Counts and activity update without showing previous-workspace data |

---

## 11. Validation UX (400)

| Step | Action | Expected |
|---|---|---|
| 1 | On `/login`, submit empty fields | Client-side validation messages appear |
| 2 | On `/register`, submit invalid email format | Client-side email error appears |

If backend validation is triggered, Network tab should show **400**, not `0`.

---

## 12. Wrong password (401)

| Step | Action | Expected |
|---|---|---|
| 1 | On `/login`, use a valid email with wrong password | Login failure message appears |
| 2 | Check Network tab | `POST /api/auth/login` returns **401** |
| 3 | Check `localStorage` | No new token stored |

---

## 13. CORS

| Step | Action | Expected |
|---|---|---|
| 1 | With backend running, perform Register or Login from `http://localhost:5173` | Request succeeds |
| 2 | Check browser console | No CORS policy errors |

Backend default allow-list includes `http://localhost:5173`.

---

## 14. Backend unavailable (network error UX)

| Step | Action | Expected |
|---|---|---|
| 1 | Stop the backend process | Backend no longer listening on `:8080` |
| 2 | Attempt Register or Login from the frontend | User sees a connection/unavailable style message |
| 3 | Check Network tab | Request fails as a network failure; UI should **not** mislabel it as HTTP 401/409 |
| 4 | Restart backend and retry login | Normal auth flow works again |

---

## Completion checklist

- [ ] Register works
- [ ] Duplicate email shows real 409 behavior
- [ ] Login works and stores token
- [ ] Refresh restores session via `/users/me`
- [ ] Protected routes redirect when logged out
- [ ] Logged-in users cannot access `/login` or `/register`
- [ ] Invalid token clears session on refresh
- [ ] Zero/one/multiple-workspace bootstrap works
- [ ] Workspace switching updates the workspace URL and Dashboard
- [ ] Dashboard counts and recent activity match the API
- [ ] Logout clears token and workspace preference
- [ ] Validation and wrong-password errors behave correctly
- [ ] CORS works from Vite dev server
- [ ] Backend unavailable shows network-style UX

When this checklist passes against a fresh local run, the auth + workspace bootstrap + Dashboard flow is ready for review handoff or regression checks.
