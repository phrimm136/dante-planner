---
name: verify
description: Drive the backend's changed auth/API flows against a running instance built from the current branch. Use when verifying backend changes end-to-end (real HTTP, real MySQL/Redis) instead of re-running tests.
---

# Backend behavioral verification

The local stack (compose project `limbusplanner`, network `danteplanner-network`) keeps
`danteplanner-backend:local` on 127.0.0.1:8080 — it is NOT your branch's code. Never restart or
replace it; run a side-by-side instance.

## Launch a branch-code instance

1. Build: `docker build -f backend/Dockerfile -t danteplanner-backend:<tag> .` (repo root context).
2. Run it as an extra compose service (overlay file adding a `backend-verify` service, image
   `<tag>`, port `127.0.0.1:18080:8080`, volumes `./static/data:/app/data:ro` and
   `./backend/src/test/resources/test-keys:/app/keys:ro`, joined to `danteplanner-network`), with
   `docker compose --project-directory . -f docker-compose.yml -f docker-compose.override.yml
   -f <overlay> -p limbusplanner up -d --no-deps backend-verify`.
3. Three values do NOT interpolate from `.env` and must be supplied explicitly:
   `JWT_PRIVATE_KEY_PATH=/app/keys/private_key.pem`, `JWT_PUBLIC_KEY_PATH=/app/keys/public_key.pem`
   (hardcode in the overlay), and `JWT_ENCRYPTION_KEY` (pass through the process env at up-time;
   read it from the running container's env — never write it to a file).
4. Health: `curl http://127.0.0.1:18080/actuator/health` (~40s startup).
5. Teardown: `docker rm -f <container>` and `docker rmi danteplanner-backend:<tag>`.

## Drive authenticated flows

- Auth is OAuth-only (no password login). Mint JWTs with the mounted test keypair instead:
  RS256, header `{"alg":"RS256"}`, claims `{"type":"access","role":"NORMAL","sub":"<userId>",
  "iat","exp"}` (refresh adds `jti` + `family_id` UUIDs, `type":"refresh"`). Sign
  `base64url(header).base64url(payload)` with `openssl dgst -sha256 -sign
  backend/src/test/resources/test-keys/private_key.pem`.
- Cookies: `accessToken` / `refreshToken` (`shared/util/CookieConstants.java`).
- Use a test user id from MySQL (`docker exec limbusplanner-mysql-1 sh -c 'mysql -u danteplanner
  -p"$MYSQL_PASSWORD" danteplanner -e "SELECT id,email,role FROM users LIMIT 5;"'`) — ids 4/5 are
  test accounts; avoid id 0 (sentinel, blocked) and real accounts.
- `GET /api/auth/me`: authenticated → 200 user JSON; guest → 200 EMPTY body (not 401).
- Auto-refresh: send an expired `accessToken` + valid `refreshToken` → response Set-Cookie carries
  a fresh pair. A self-minted refresh (unknown family) is accepted via the rotation legacy branch.
- `POST /api/auth/logout` returns 204; needs the `csrf` cookie + `X-CSRF-Token` header (value =
  csrf cookie, issued on any prior response).
- Rejections surface as `WARN ... Security event: <CODE> (<REASON>)` in container logs.

## Gotchas

- `.claude/skills/route-tester` describes a different codebase (Node/Keycloak) — ignore it.
- The drive writes rotation families / blacklist entries to the shared auth Redis (TTL-bound);
  use a test user so residue is inert.
- Never run two gradle invocations concurrently on `backend/` — they corrupt each other's
  `build/test-results`.
