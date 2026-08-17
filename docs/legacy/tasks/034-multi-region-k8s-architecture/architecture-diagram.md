# Multi-Region Architecture — VPC communication map

Text diagram of the two-region k3s fleet, the shared RDS, and every cross-VPC path.
CIDRs and ports are the live values.

## Legend

```
═══  public internet / edge          ──▶  intra-VPC (same VPC, SG-gated)
╌╌╌  VPC peering (private)            ⇢    cross-region AWS-managed (replication)
[SG] security group gate             (:port) TCP port
```

## Top-level entry plane

```
                              ┌─────────────┐
   end users  ═══════════════▶│  Cloudflare │  (proxy + WAF; mTLS Authenticated
                              │   edge      │   Origin Pull = deferred, SG is gate)
                              └──────┬──────┘
                            api.dante-planner.com → A →
                                     ▼
                   ┌──────────────────────────────────────┐
                   │  AWS Global Accelerator (anycast)     │
                   │  166.117.53.62 , 99.83.184.149        │
                   │  proximity routing · ~30s failover    │
                   │  client-IP preserved (EC2 endpoints)  │
                   └───────┬───────────────────────┬───────┘
        health :443 /healthz-local          health :443 /healthz-local
        (Route53 health-checker ranges → [SG ingress])
                   ▼                               ▼
        ╔══════════════════════╗        ╔══════════════════════╗
        ║ OREGON (us-west-2)   ║        ║ SEOUL (ap-northeast-2)║
        ║ ingress EC2 :443     ║        ║ ingress EC2 :443     ║
        ║ (EIP, Traefik)       ║        ║ (Traefik)            ║
        ╚══════════════════════╝        ╚══════════════════════╝
```

## Region internals (identical shape — the reusable `modules/fleet`)

Each region is one VPC, one k3s cluster, public subnets only (no NAT; SGs are the
boundary). Four node roles; Spring runs as a DaemonSet on `role=app`.

```
  OREGON fleet VPC  10.20.0.0/16                SEOUL fleet VPC  10.30.0.0/16
  ┌───────────────────────────────┐            ┌───────────────────────────────┐
  │ CP (pet)   k3s server + etcd   │            │ CP (pet)   k3s server + etcd   │
  │            ArgoCD core         │            │            ArgoCD core         │
  │ ingress(pet) Traefik :443      │            │ ingress(pet) Traefik :443      │
  │ data (pet)  redis-auth  (SS)   │            │ data (pet)  redis-auth  (SS)   │
  │             redis-ratelimit    │            │             redis-ratelimit    │
  │             prometheus         │            │             prometheus         │
  │ app ASG(cattle) Spring DS ─┐   │            │ app ASG(cattle) Spring DS ─┐   │
  │  [cluster SG]              │   │            │  [cluster SG]              │   │
  └───────────────────────────┼───┘            └───────────────────────────┼───┘
                              intra-VPC ──▶                                intra-VPC ──▶
   Spring → redis-auth.svc:6379 (auth reads/writes, local)
   Spring → redis-ratelimit.svc:6379 (rate buckets, local, never replicated)
   Spring → redis-auth.svc:6379 (SSE pub/sub, local)
```

## The shared database (one RDS, two regions read/write it)

```
   RDS VPC  172.31.0.0/16  (us-west-2)
   ┌────────────────────────────────────────────┐
   │  aws_db_instance.this  = PRIMARY (MySQL)    │
   │    encrypted (us-west-2 KMS key)            │
   │    [RDS SG :3306]                           │
   └───────┬───────────────────────┬────────────┘
           ╎ peering               ╎ peering (cross-region)
           ╎ oregon-to-rds         ╎ seoul-to-rds
           ▼                       ▼
   Oregon Spring ──(:3306)──▶ PRIMARY ◀──(:3306)── Seoul Spring   (WRITES: write-global)

   PRIMARY  ⇢⇢⇢ AWS-managed cross-region replication ⇢⇢⇢  Seoul read replica
                                              danteplanner-mysql-seoul (ap-northeast-2)
                                              encrypted (ap-northeast-2 KMS key — re-encrypted;
                                              keys are region-scoped, so a DIFFERENT ARN)
   Seoul Spring ──(:3306, local)──▶ Seoul read replica          (READS: read-local)
```

## The three VPC peerings (why each exists)

```
  #1  Oregon fleet 10.20/16  ╌╌╌╌╌╌╌  RDS 172.31/16     same-region, auto-accept
        why: Oregon app/data nodes reach the private RDS on :3306.

  #2  Seoul fleet 10.30/16   ╌╌╌╌╌╌╌  RDS 172.31/16     cross-region, explicit accepter
        why: Seoul WRITES reach the primary on :3306 (write-global). Return route +
        3306 rule added on the RDS side by CIDR (SG refs don't cross regions).

  #3  Seoul fleet 10.30/16   ╌╌╌╌╌╌╌  Oregon fleet 10.20/16   cross-region, accepter
        why: the auth-redis cross-region path (below). Routes on BOTH sides.
```

## Cross-region auth-redis: read-local / write-global (over peering #3)

```
  Seoul Spring  ──WRITES (blacklist/rotation/tombstone)──▶  redis-auth.oregon.danteplanner.internal :31637
                                                            (Route53 private zone → Oregon data node
                                                             10.20.0.39, reached over peering #3)
                                                                     │
  Oregon data node: redis-auth NodePort :31637  ◀── [cluster SG admits 10.30.0.0/16 only]
                                                                     │
  Seoul redis-auth (StatefulSet)  ──REPLICAOF (masterauth)──────────┘
     read-only replica of the Oregon primary redis-auth
                     │
  Seoul Spring  ──READS (blacklist/tombstone, local)──▶ Seoul redis-auth.svc:6379   (read-local)

  Security: protected-mode yes + requirepass/masterauth (password from the
  runtime-config bundle). Cross-region REPLICAOF happens ONLY with the password;
  no-password path runs standalone (never replicates over peering unauthenticated).
```

## Name resolution (Route53 private hosted zone)

```
  zone: danteplanner.internal   (private hosted zone)
    associated with  →  Oregon fleet VPC   (created here)
    associated with  →  Seoul fleet VPC    (aws_route53_zone_association, cross-region)
    record: redis-auth.oregon.danteplanner.internal  A  → 10.20.0.39 (Oregon data node)

  A private zone resolves ONLY from associated VPCs, so BOTH associations are
  required: Seoul must resolve the name, Oregon owns the record. DNS gives the IP;
  peering #3 makes it reachable — two independent layers.
```

## Control-plane / supporting AWS services (region-scoped, replicated)

```
  Secrets Manager (us-west-2)  ⇢ multi-region replica ⇢  (ap-northeast-2)
     danteplanner/backend/runtime-config, jwt/*, origin-tls
     each region's External Secrets Operator reads its LOCAL replica
     (Seoul SecretStore region-patched to ap-northeast-2)

  ECR (us-west-2)  ⇢ cross-region replication ⇢  (ap-northeast-2)
     danteplanner-backend image; each region's kubelet pulls LOCALLY

  SSM Session Manager       → operate each CP (no SSH, no public port)
  ArgoCD (per region)       → GitOps sync from the branch; NOT hub-spoke
  Prometheus (per region)   → local scrape → Grafana Cloud (survives a region loss)
```

## One-line summary of who talks to whom

| From | To | Path | Port |
|------|----|----|------|
| Users | Cloudflare → GA → regional ingress | public/anycast | 443 |
| GA health | ingress `/healthz-local` | public :443 (SG: R53 health ranges) | 443 |
| Spring (either region) | RDS **primary** (writes) | peering #1 / #2 | 3306 |
| Seoul Spring | Seoul RDS **replica** (reads) | intra-VPC | 3306 |
| Seoul Spring | Oregon redis-auth (writes) | peering #3, via Route53 name | 31637 |
| Seoul redis-auth | Oregon redis-auth (REPLICAOF) | peering #3, via Route53 name | 31637 |
| Spring | local redis (auth-read/ratelimit/sse) | intra-VPC | 6379 |
| ESO (each region) | Secrets Manager (local replica) | AWS API | 443 |
| kubelet (each region) | ECR (local replica) | AWS API | 443 |
```
