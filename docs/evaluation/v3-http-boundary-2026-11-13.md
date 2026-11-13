# V3 live HTTP boundary acceptance — 2026-11-13

**Result: PARTIAL — blocked before the three requests.** No MEMBER `403`, size-limit `413`, or worker-exhaustion result is claimed.

## Precondition and observed blocker

- Target branch: `feat/v3-knowledge-rag`; PR #10 was open and unmerged.
- Verified PR head: `dad1787511a54758b86f80d08054176ba930c0a4`.
- The selected Docker context was `desktop-linux`, using `/Users/joan/.docker/run/docker.sock`.
- The Docker socket existed, but bounded `docker ps` probes did not return. Docker Desktop was launched with `open -a Docker`; the daemon still did not answer a 12-second probe.
- `GET http://localhost:8084/api/health` failed to connect. The isolated backend was therefore unavailable before test setup.
- No HTTP boundary request or synthetic fixture write was made. No service/Compose configuration or application code was changed. The original `opspilot-mysql` container and its volume were not targeted or modified; their health could not be rechecked while the daemon was unavailable.

## Requested checks

| Check | Result | Evidence |
|---|---|---|
| OWNER reindex accepted; actual MEMBER reindex denied with `403`; no task/limiter side effects; other workspace isolated | **BLOCKED — not run** | No backend listener was available, so no real HTTP request or response exists. |
| 501 synthetic active documents; real reindex request returns `413`; no index state/cooldown side effect | **BLOCKED — not run** | The isolated MySQL could not be reached through Docker. No synthetic documents were inserted. |
| Real HTTP reindex accepted; AI dependency failure; worker reaches persisted `FAILED` after bounded retries; service recovery | **BLOCKED — not run** | The isolated backend and AI service could not be controlled or queried. No failure injection was performed. |

Existing backend unit tests cover the corresponding MEMBER authorization, document-count guard, and `MAX_ATTEMPTS = 8` behavior. They do not replace these requested real HTTP and worker-persistence checks.

## Current-head CI and other evidence

The implementation SHA above has successful PR workflows: [Backend CI run 42602909666](https://github.com/JoanH-project/opspilot/actions/runs/42602909666), [AI Service CI run 42602909619](https://github.com/JoanH-project/opspilot/actions/runs/42602909619), and [Frontend CI run 42602909697](https://github.com/JoanH-project/opspilot/actions/runs/42602909697). Backend CI covers its Testcontainers integration and Java 21 build. These CI results do not establish the three blocked local HTTP checks.

The later documentation commit `1443fd60f11d2749a23b36d7001293395bf21dc9` had AI Service CI [run 43374135506](https://github.com/JoanH-project/opspilot/actions/runs/43374135506) and Frontend CI [run 43374135550](https://github.com/JoanH-project/opspilot/actions/runs/43374135550) pass. Backend CI [run 43374135597](https://github.com/JoanH-project/opspilot/actions/runs/43374135597) failed because the `ubuntu-latest` runner used a Docker daemon with minimum API 1.40 while Testcontainers requested API 1.32. `.github/workflows/backend-ci.yml` now pins the backend runner to Ubuntu 24.04; verification on the updated workflow is pending. This CI issue is separate from the unavailable local Docker daemon that blocked the three HTTP checks.

Prior browser flows and the small synthetic retrieval evaluation remain as previously recorded in [`V3_ACCEPTANCE.md`](../V3_ACCEPTANCE.md). The evaluation fixture is regression evidence only and makes no production-quality claim.

**Next acceptance action:** restore a functioning local Docker Desktop daemon, confirm only the isolated `opspilot-v3-local` stack is used, then run all three checks and record sanitized request/response plus database-state evidence. Keep PR #10 open. Do not start V4.
