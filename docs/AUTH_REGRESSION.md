# OpsPilot Auth Regression (Post PR #4)

Repeatable regression checks for the Auth Stabilization fixes merged in PR #4.

**Related fixes:**
1. **HTTP error preservation** — backend `400/401/403/404/409/500` keep real `status`, `message`, and `fieldErrors`; only true network failures become `status: 0` / `NetworkError`.
2. **Login commit order** — token, `currentUser`, and `localStorage` are committed only after `GET /api/users/me` succeeds. If `/users/me` fails, no new token remains and the user stays logged out.

**Contract source:** `docs/API_CONTRACT.md`  
**Broader F1 checklist:** `docs/FRONTEND_SMOKE_TEST.md`  
**Token key:** `opspilot_access_token` in `localStorage`

This document is intentionally narrow. It does **not** replace full Phase 1–8 backend verification.

---

## When to run

- After auth/client changes
- Before Frontend F2 acceptance that reuses the auth path
- After any regression report involving 409 / 401 / network / login token persistence

## Prerequisites

1. MySQL, backend (`:8080`), and frontend (`:5173`) are running
2. Browser DevTools open (Network + Application → Local Storage)
3. Use a unique email per register run, for example `auth-reg-<timestamp>@example.com`

---

## R1 — Duplicate registration keeps HTTP 409

| Step | Action | Expected |
|---|---|---|
| 1 | Register a new user successfully | Account created; redirect/login available |
| 2 | Register again with the **same email** | Page shows a duplicate-email / conflict meaning |
| 3 | DevTools → Network → `POST /api/auth/register` | Status is **409**, not `0` |
| 4 | Read the UI message | Must **not** look like a generic “unable to connect / network disconnected” message |

**Pass criteria:** Network = `409`; UI reflects conflict, not network failure.

---

## R2 — Wrong password keeps HTTP 401

| Step | Action | Expected |
|---|---|---|
| 1 | Open `/login` with a known registered email | Form ready |
| 2 | Submit with an incorrect password | Page shows a generic authentication failure meaning |
| 3 | DevTools → Network → `POST /api/auth/login` | Status is **401**, not `0` |
| 4 | Check `localStorage` | No new `opspilot_access_token` stored |

**Pass criteria:** Network = `401`; user remains unauthenticated; no token left behind.

---

## R3 — Network failure UX and recovery

| Step | Action | Expected |
|---|---|---|
| 1 | Stop the backend (or block `localhost:8080`) | Backend unreachable |
| 2 | Attempt Login or Register | Page shows connection / unable-to-connect failure |
| 3 | DevTools → Network | Failure is a network-level failure; UI must **not** present it as HTTP `401` or `409` |
| 4 | Restart backend | Service healthy again |
| 5 | Retry the same Login/Register | Succeeds with normal auth behavior |

**Pass criteria:** Offline path shows connection failure; after recovery, retry succeeds.

---

## R4 — Login succeeds but `/users/me` fails leaves no token

Goal: prove the PR #4 commit-order fix.

Suggested simulation (Chrome / Edge):

1. Open DevTools → Network
2. Enable request blocking (or similar override) for `**/api/users/me`
3. On `/login`, submit valid credentials
4. Confirm `POST /api/auth/login` returns **200** with an `accessToken`
5. Confirm `GET /api/users/me` fails (blocked / refused / non-200)
6. Check Application → Local Storage
7. Disable the block and optionally retry

| Check | Expected |
|---|---|
| After failed `/users/me` | `opspilot_access_token` is **absent** (no new token persisted) |
| Auth UI state | User remains on login / unauthenticated |
| Protected route | `/dashboard` still redirects to `/login` |
| Error surfacing | Original `/users/me` failure is not silently ignored |

**Pass criteria:** temporary login token is never committed when `/users/me` fails; session stays logged out.

---

## Result recording template

Copy into a PR comment or `TEAM_STATUS` handoff when reporting:

```text
Auth regression (docs/AUTH_REGRESSION.md)
- R1 duplicate email 409: PASS / FAIL / SKIPPED
- R2 wrong password 401: PASS / FAIL / SKIPPED
- R3 network failure + recovery: PASS / FAIL / SKIPPED
- R4 login OK + /users/me fail leaves no token: PASS / FAIL / SKIPPED

Environment notes:
- Browser available: yes / no
- Docker / backend local run: yes / no
```

### Reporting rules

- Do **not** record simulated/unit-style checks as “browser PASS”.
- If the environment has no browser or no running backend, mark browser checks as **pending** and state the limitation honestly.
- If a real auth defect is found, prioritize fixing or escalating it over continuing unrelated QA work.

---

## Completion checklist

- [ ] R1 — duplicate registration preserves HTTP 409 semantics
- [ ] R2 — wrong password preserves HTTP 401 semantics
- [ ] R3 — network failure UX + successful retry after recovery
- [ ] R4 — `/users/me` failure after login leaves no new token

When all four pass in a real browser against a local full stack, Auth Stabilization is regression-verified for F2 reuse.
