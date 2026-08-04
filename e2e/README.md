# End-to-end suites

Playwright driving a running stack over HTTP. Three projects, split by what they need:

| Project | `yarn --cwd e2e …` | Needs |
|---|---|---|
| `contract` | `test:contract` | the request fixture only, against the API host |
| `infra` | `test:infra` | the request fixture, plus Toxiproxy and both regions |
| `chromium` | `test:app` | a browser, and an SPA build behind nginx |

---

## Bringing the stack up

```
yarn --cwd frontend build          # nginx serves ./frontend/dist; the image ships nothing
STUB_CLIENT_SECRET=stub-secret yarn --cwd e2e stack:up:rig
```

`stack:up:rig` reads `STUB_CLIENT_SECRET` from the environment rather than from `.env`, and exits
before starting anything if it is unset.

Without the frontend build the bind mount in `docker-compose.e2e.yml` is an empty directory,
nginx's `try_files … /index.html` finds no `index.html`, and every route 404s — which
`routes.spec.ts` reads as a failure of the route, not of the mount.

## Running the suites locally

```
E2E_BASE_URL=http://localhost \
E2E_API_URL=http://localhost:8082 \
E2E_API_SEOUL_URL=http://localhost:8082 \
E2E_API_OREGON_URL=http://localhost:8081 \
E2E_IDP_URL=http://localhost:3000 \
E2E_DB_HOST=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' limbusplanner-mysql-1) \
E2E_DB_USER=$(docker exec limbusplanner-mysql-1 printenv MYSQL_USER) \
E2E_DB_PASSWORD=$(docker exec limbusplanner-mysql-1 printenv MYSQL_PASSWORD) \
E2E_STAGING_KEY_FILE=../backend/src/test/resources/test-keys/private_key.pem \
yarn --cwd e2e test:app
```

Three of those are not guessable, and each fails in a way that names something other than itself:

- **`E2E_DB_HOST` must be the primary's bridge address.** The rig publishes a host port for the
  replica (`3307`) and none at all for the primary, so `seed.ts`'s `127.0.0.1` default reaches
  whatever else holds `3306` on the host — or nothing, and reports a tunnel that was never
  involved.
- **`E2E_DB_USER` / `E2E_DB_PASSWORD` are the compose application credentials**, the
  `MYSQL_USER` / `MYSQL_PASSWORD` pair in the repo-root `.env`. `seed.ts` throws by hand when
  either is missing, with a message about `rds-tunnel.sh` that a local run has nothing to do with.
- **`E2E_STAGING_KEY_FILE` must point at the test keypair.** `docker-compose.override.yml` mounts
  `backend/src/test/resources/test-keys` into the backend, so that private key is what the local
  stack verifies against. Unset, `auth.ts` shells out to AWS Secrets Manager under a profile a
  local checkout has no reason to hold, and the suite fails at `aws` rather than at the token.

## What a local run cannot cover

The `chromium` and `read-your-writes` suites address Seoul and Oregon separately. A single-region
stack (`stack:up`) answers only one of them, so those specs fail on connection rather than on
behaviour. Only `stack:up:rig` provisions both.

## What a browser spec has to arrange

Three preconditions cost more debugging time than everything else here, and all three fail as a
timeout somewhere unrelated:

- **Choose the account's sync preference.** A fresh account's `syncEnabled` is null, and
  `GlobalLayout` answers that with a modal that intercepts every pointer event on the page.
  `seedPlanner` sets it, which is also what makes a manual save reach the network at all.
- **Seed IndexedDB, not just the server.** `/planner/md/$id` and `/planner/md/$id/edit` read
  `loadFromLocal` with no server fallback, so a planner created through the API alone renders the
  not-found page. `src/localPlanner.ts` writes the row before the app's first script.
- **Wait for the element, not for the page.** The crawler skeleton going means the shell mounted;
  comment composers and viewer sections arrive in later lazy chunks. `settled` reports the
  document stable long before they land, so every gesture waits on its own target.

`actionTimeout` is set in `playwright.config.ts` because Playwright's default is no timeout at
all: a click on an element that never becomes actionable otherwise burns the whole test budget and
reports `Test timeout exceeded` without naming the locator it was waiting on.

## Conventions

`docs/testing-principles.md` governs what to assert. Two rules bite hardest here:

- A test owns the rows it creates (§13). Every spec seeds its own user and asserts on its own
  title — never on a list's length or its first entry.
- `waitUntil: 'networkidle'` is banned, and PR Gate greps for it. An authenticated page opens an
  SSE stream and holds it, so the network never goes idle and the wait burns the full timeout.
  Wait for `domcontentloaded` and then assert on something the page renders.

`src/gestures.ts` carries the browser-tier fixtures — `settled`, `withoutScrollShift`,
`producesRequest` — and documents the `page.clock` interaction that freezes progressive reveal.
