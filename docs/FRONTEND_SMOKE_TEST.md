# OpsPilot V1 Frontend F1 Smoke Test

Reusable manual browser checklist for Frontend F1 (Auth + App Shell). Run this after MySQL, the backend, and the frontend dev server are all running locally.

**Prerequisites:** see root `README.md` and `frontend/README.md`.

**Local URLs:**
- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`

**Token storage key:** `opspilot_access_token` in `localStorage`.

Use a fresh email per run when registering, for example `f1-smoke-<timestamp>@example.com`.

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
| 2 | Sign in with the registered account | Redirects to `/dashboard` |
| 3 | Check top bar | Shows the user's name |
| 4 | Check `localStorage` | `opspilot_access_token` is set |

---

## 4. Current user restoration (`/users/me`)

| Step | Action | Expected |
|---|---|---|
| 1 | While logged in, confirm dashboard loads | User name visible in app shell |
| 2 | Hard refresh the page (`Ctrl+R` / `Cmd+R`) | Brief loading state, then dashboard returns |
| 3 | Check Network tab on refresh | `GET /api/users/me` returns **200** |

Session should restore from the stored token without requiring login again.

---

## 5. ProtectedRoute

| Step | Action | Expected |
|---|---|---|
| 1 | Log out | Returns to login flow |
| 2 | Manually open `/dashboard` | Redirects to `/login` |
| 3 | Manually open `/projects` or `/documents` while logged out | Redirects to `/login` |
| 4 | Log in again | Can access protected routes |

---

## 6. Authenticated auth-route redirect

| Step | Action | Expected |
|---|---|---|
| 1 | While logged in, open `/login` | Redirects to `/dashboard` |
| 2 | While logged in, open `/register` | Redirects to `/dashboard` |

---

## 7. Invalid token handling

| Step | Action | Expected |
|---|---|---|
| 1 | Log in successfully | Dashboard visible |
| 2 | In DevTools → Application → Local Storage, change `opspilot_access_token` to `invalid-token` | Token edited |
| 3 | Hard refresh | User is treated as logged out |
| 4 | Check storage after refresh | Token removed from `localStorage` |
| 5 | Attempt `/dashboard` | Redirects to `/login` |

---

## 8. Logout

| Step | Action | Expected |
|---|---|---|
| 1 | Log in again | Dashboard visible |
| 2 | Click **Logout** in the app shell | Returns to unauthenticated state |
| 3 | Check `localStorage` | `opspilot_access_token` removed |
| 4 | Open `/dashboard` | Redirects to `/login` |

---

## 9. Validation UX (400)

| Step | Action | Expected |
|---|---|---|
| 1 | On `/login`, submit empty fields | Client-side validation messages appear |
| 2 | On `/register`, submit invalid email format | Client-side email error appears |

If backend validation is triggered, Network tab should show **400**, not `0`.

---

## 10. Wrong password (401)

| Step | Action | Expected |
|---|---|---|
| 1 | On `/login`, use a valid email with wrong password | Login failure message appears |
| 2 | Check Network tab | `POST /api/auth/login` returns **401** |
| 3 | Check `localStorage` | No new token stored |

---

## 11. CORS

| Step | Action | Expected |
|---|---|---|
| 1 | With backend running, perform Register or Login from `http://localhost:5173` | Request succeeds |
| 2 | Check browser console | No CORS policy errors |

Backend default allow-list includes `http://localhost:5173`.

---

## 12. Backend unavailable (network error UX)

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
- [ ] Logout clears token and protected routes
- [ ] Validation and wrong-password errors behave correctly
- [ ] CORS works from Vite dev server
- [ ] Backend unavailable shows network-style UX

When this checklist passes against a fresh local run, Frontend F1 is ready for review handoff or post-merge regression checks.
