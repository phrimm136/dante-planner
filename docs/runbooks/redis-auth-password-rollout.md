# Rolling out a change to the auth Redis

Ships a change to Oregon's auth Redis (`deploy/base/redis-auth.yaml`) without cutting its clients
off. The server always starts with `--requirepass` from `AUTH_REDIS_PASSWORD`, so every restart
leaves it refusing any client that does not hold the password. The restart itself is not a step
here: ArgoCD syncs from `main`, and a sync that changes the StatefulSet's pod template restarts
`redis-auth-0`. The only thing the operator controls is when the change reaches `main`.

Prerequisites: an AWS session in the prod account (`AWS_PROFILE=prod`), `jq`, and SSM access to the
Oregon control plane. `scripts/ops/redis-auth-restart.sh` resolves the CP from
`terraform/oregon` outputs; set `CP_INSTANCE_ID` to skip that lookup.

## The one ordering rule

> **The pre-checks pass before the change reaches `main`, never after.**

Once merged, the restart follows on the next sync with no further gate. A backend pod without the
password at that moment loses the auth store, and its blacklist checks fail open until the
pod restarts with the key.

The order works because the backend's Redis client (Lettuce 6, protocol not pinned) authenticates
with `HELLO 3 AUTH default <password>`, which a Redis without a password accepts; the one-argument
`AUTH <password>` would be rejected ("ERR AUTH <password> called without any password configured
for the default user"), so pinning the client to RESP2 would break this order.

## 1. The password is in the runtime secret

`AUTH_REDIS_PASSWORD` lives in the `danteplanner/backend/runtime-config` Secrets Manager JSON and
reaches pods through ESO as the `backend-runtime-config` Secret. If it is missing, add it there and
wait for ESO to refresh the Secret before going further.

## 2. Every client holds it

```bash
AWS_PROFILE=prod scripts/ops/redis-auth-restart.sh --dry-run
```

The dry run must log `pre-checks passed`. Its `re-run with --apply to restart` hint on that line is
not followed for this rollout. It fails when the Secret lacks the key or any backend pod
reports `MISSING`; a pod reports `MISSING` when it started before the Secret carried the key.
Rollout-restart the backend DaemonSet and re-run until it passes.

## 3. Merge the change to `main`

Only now. The ArgoCD sync performs the restart, so `--apply` is not run: it would restart the pod
a second time, outside the sync.

## 4. Verify

After the sync, `redis-auth-0` is Ready and requires AUTH, and Seoul's replica catches up:

```bash
AWS_PROFILE=prod scripts/ops/access/metrics-query.sh 'max(redis_master_repl_offset{job="redis-auth",cluster="seoul"})' 30m 1m
AWS_PROFILE=prod scripts/ops/access/metrics-query.sh 'sum(redis_db_keys_expiring{job="redis-auth"}) by (cluster)' 30m 1m
```

The Seoul offset climbs above 0 and keys with expiry reappear. The
`redis-auth-replica-unsynced` and `redis-auth-replica-link-down` alerts cover the same signals.
