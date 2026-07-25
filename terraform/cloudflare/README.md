# terraform/cloudflare

The two-region front door: a named tunnel per region and the load balancer that steers to them.
Replaces the Global Accelerator stack in `../global-accelerator`.

Applied once and globally, like the stack it replaces — the front door must survive either
region's rebuild, so it lives outside `../oregon` and `../seoul`.

## Before the first apply

Two subscriptions on the zone, and each carries a stop condition:

| Item | Expected | Stop if |
|---|---|---|
| Load Balancing | ~$5/mo | — |
| Traffic Steering add-on | ~$10/mo | it requires a Pro zone — the cost case inverts, so stop and re-decide |
| Minimum monitor interval | ≤60s | the floor is higher — the failover window needs re-ratifying, then set `monitor_interval_seconds` |

Record the real numbers before relying on them; `monitor_interval_seconds` defaults to the
assumed floor, not a measured one.

## Applying

```bash
cp terraform.tfvars.example terraform.tfvars   # fill in token, account, zone
terraform init
terraform plan                                  # expect creates only
terraform apply
```

Point `api_hostname` at a throwaway hostname for the first apply. Both paths then run at once
and production traffic is untouched while you confirm each tunnel reports **≥4 edge
connections** — one connection is a single edge location away from an outage. Moving
`api_hostname` to the real hostname is the cutover; removing it again is the rollback, for as
long as the accelerator is still standing.

## What this stack does not own

- **cloudflared itself.** The Deployments live under `deploy/`. This stack outputs the tunnel
  tokens; put each in its region's Secrets Manager entry and let the `ExternalSecret` deliver
  it. A hand-authored Kubernetes Secret is not how secrets reach this cluster.
- **The origin.** `origin_service` points at the ingress node's Traefik listener. Traefik, the
  `role=ingress` instances, and the `origin-tls` secret are all retained — the tunnel replaces
  the accelerator, not the ingress. Traefik's TLSOption must stay `VerifyClientCertIfGiven`:
  hardening it to require a client certificate brings down a tunnel that presents none.
- **The CA bundle.** `origin_ca_pool_path` is a path *inside* the cloudflared container. The
  Deployment mounts the file; this stack only says where to look.

## Operational notes

Never health-check through the proxied public hostname — bot protection answers 521/403 and
you will chase an outage that is not happening. The load balancer's own monitors are internal
probes and are exempt; deploy-time verification stays on SSM and kubectl.

The window sequence, drills, and teardown ledger live in
`../../docs/tasks/043-planner-schema-decomposition/migration-runbook.md`.
