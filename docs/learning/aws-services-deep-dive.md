# AWS Services Deep Dive

A detailed explanation of each AWS service, what it does, why it exists, and how it fits into a web application deployment.

---

## EC2 (Elastic Compute Cloud)

### What Problem Does It Solve?
Before cloud computing, deploying an application meant:
- Buying physical servers ($5,000-50,000+)
- Waiting weeks for delivery
- Hiring staff to maintain hardware
- Guessing capacity needs years in advance

EC2 solves this by providing **virtual servers on demand**. You can launch a server in minutes and pay only for what you use.

### How It Works Internally
```
Physical AWS Data Center
├── Rack of Physical Servers
│   └── Hypervisor (Xen/Nitro)
│       ├── Your EC2 Instance (isolated VM)
│       ├── Another Customer's Instance
│       └── Another Customer's Instance
```

The hypervisor creates isolated virtual machines. Your instance cannot see or access other instances on the same physical hardware.

### Instance Types Explained

**Naming Convention:** `t3.small`
- `t` = Family (T = burstable, M = general purpose, C = compute optimized)
- `3` = Generation (higher = newer hardware)
- `small` = Size (nano < micro < small < medium < large < xlarge)

| Family | Best For | Example Use |
|--------|----------|-------------|
| **T** (Burstable) | Variable workloads, dev/test | Web servers with occasional spikes |
| **M** (General) | Balanced compute/memory | Production application servers |
| **C** (Compute) | CPU-intensive tasks | Video encoding, scientific computing |
| **R** (Memory) | Memory-intensive | Large databases, in-memory caching |
| **G/P** (GPU) | Graphics/ML | Machine learning training |

### Burstable Instances (T-series) Deep Dive
T3/T4g instances use **CPU credits**:
- Baseline: t3.small gets 20% CPU continuously
- When idle: Accumulates credits (up to 576 credits)
- When busy: Spends credits to burst to 100% CPU
- Credits depleted: Throttled to 20% baseline

**Implication:** Great for web apps (idle most of time, burst on requests). Bad for constant CPU load.

### EBS (Elastic Block Store)
EC2 instances need storage. EBS provides virtual hard drives.

```
EC2 Instance ←──network──→ EBS Volume
                           (persists independently)
```

**Volume Types:**
| Type | IOPS | Use Case |
|------|------|----------|
| gp3 | 3,000-16,000 | General purpose (default choice) |
| io2 | Up to 64,000 | High-performance databases |
| st1 | 500 | Throughput-optimized (big data) |
| sc1 | 250 | Cold storage (infrequent access) |

**Key Point:** EBS volumes persist when instance stops. You can detach and attach to different instances.

### Security Groups In Depth

Security Groups are **stateful firewalls**:
```
Inbound: Allow TCP 80 from 0.0.0.0/0
         ↓
Request comes in on port 80
         ↓
Response goes out automatically (stateful)
         ↓
No outbound rule needed for response
```

**Stateful vs Stateless:**
- **Security Group (Stateful):** Return traffic automatically allowed
- **Network ACL (Stateless):** Must explicitly allow both directions

**Best Practice:** Start with deny-all, add only needed rules.

### Elastic IP

**Problem:** EC2 public IPs change when instance stops/starts.
**Solution:** Elastic IP = static IP address you control.

```
Elastic IP (52.10.20.30)
    │
    └──→ Attached to EC2 Instance A
         (instance stops)
    └──→ Re-attach to EC2 Instance B
         (IP stays same)
```

**Cost:** Free when attached to running instance. $0.005/hour when unattached (to discourage hoarding).

---

## S3 (Simple Storage Service)

### What Problem Does It Solve?
Traditional file storage problems:
- Hard drives fail (data loss)
- Capacity limits (run out of space)
- Scaling is manual (buy more drives)
- Backup complexity

S3 provides **infinite, durable object storage**:
- 99.999999999% durability (11 nines)
- Automatically replicated across facilities
- No capacity planning needed
- Pay per GB used

### Object Storage vs File Storage vs Block Storage

```
Block Storage (EBS):
├── Raw blocks, OS manages filesystem
├── Mountable as drive
└── Use for: Databases, OS disks

File Storage (EFS):
├── Hierarchical filesystem
├── Multiple instances access same files
└── Use for: Shared application data

Object Storage (S3):
├── Flat namespace (bucket + key)
├── Access via HTTP API
└── Use for: Backups, static assets, data lakes
```

### S3 Consistency Model (2020 Update)
S3 now provides **strong read-after-write consistency**:
- PUT new object → immediately readable
- UPDATE object → immediately see new version
- DELETE object → immediately gone

Previously S3 had eventual consistency, causing subtle bugs.

### Storage Classes

| Class | Availability | Use Case | Cost/GB/month |
|-------|--------------|----------|---------------|
| Standard | 99.99% | Frequently accessed | $0.023 |
| Intelligent-Tiering | 99.9% | Unknown access pattern | $0.023 + monitoring |
| Standard-IA | 99.9% | Infrequent access | $0.0125 |
| Glacier | 99.99% | Archive (minutes retrieval) | $0.004 |
| Glacier Deep Archive | 99.99% | Archive (hours retrieval) | $0.00099 |

**Lifecycle Rules:** Automatically transition objects between classes.
```
Day 0:   Upload to Standard
Day 30:  Move to Standard-IA
Day 90:  Move to Glacier
Day 365: Delete
```

### Bucket Policies vs IAM Policies

**IAM Policy:** "User X can access bucket Y"
```json
{
  "Effect": "Allow",
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::my-bucket/*"
}
```

**Bucket Policy:** "Bucket Y allows user X"
```json
{
  "Effect": "Allow",
  "Principal": {"AWS": "arn:aws:iam::123456789:user/X"},
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::my-bucket/*"
}
```

Both achieve same result. Use bucket policies for cross-account access or public access rules.

### S3 Access Logging

Records every request to your bucket:
```
79a59df... my-bucket [06/Feb/2024:00:00:49 +0000] 192.0.2.3
arn:aws:iam::123:user/admin 3E57A... REST.GET.OBJECT
backup.sql.gz "GET /backup.sql.gz HTTP/1.1" 200 - 1024000
1024000 50 49 "-" "aws-cli/2.0" - ... SigV4 ECDHE-RSA-AES128-SHA ...
```

Fields include: bucket owner, time, requester IP, IAM identity, operation, object key, HTTP status, bytes transferred, time taken.

---

## SSM (Systems Manager)

### What Problem Does It Solve?
Traditional server management problems:
- SSH requires port 22 open (security risk)
- SSH keys must be distributed and rotated
- No audit trail of commands run
- Difficult to run same command on many servers

SSM provides **secure, audited remote management** without SSH.

### How SSM Run Command Works

```
1. You (GitHub Actions)
   │
   │ aws ssm send-command --document "AWS-RunShellScript"
   ▼
2. AWS SSM Service (regional)
   │
   │ Routes command to target instance
   ▼
3. SSM Agent (runs on EC2)
   │
   │ Polls SSM service for commands
   │ Executes received commands
   │ Returns output
   ▼
4. AWS SSM Service
   │
   │ Stores output
   ▼
5. You retrieve output
   aws ssm get-command-invocation
```

**Key Insight:** SSM Agent polls outbound. No inbound ports needed.

### Parameter Store Deep Dive

**Types:**
| Type | Encryption | Use Case |
|------|------------|----------|
| String | None | Config values (APP_ENV=production) |
| StringList | None | Comma-separated values |
| SecureString | KMS | Secrets (passwords, API keys) |

**Hierarchy:**
```
/danteplanner/
├── prod/
│   ├── MYSQL_PASSWORD
│   ├── JWT_SECRET
│   └── SENTRY_DSN
└── dev/
    ├── MYSQL_PASSWORD
    └── JWT_SECRET
```

Access by path: `aws ssm get-parameters-by-path --path /danteplanner/prod`

**Parameter Store vs Secrets Manager:**
| Feature | Parameter Store | Secrets Manager |
|---------|-----------------|-----------------|
| Cost | Free (standard) | $0.40/secret/month |
| Rotation | Manual | Automatic |
| Cross-account | Limited | Built-in |
| Best for | Config + simple secrets | Database credentials |

### Session Manager

Browser-based shell without SSH:
```
AWS Console → Systems Manager → Session Manager → Start Session
    ↓
Browser-based terminal to EC2
    ↓
Full audit log in CloudTrail
```

No SSH keys, no port 22, no bastion hosts.

---

## CloudWatch

### What Problem Does It Solve?
Without monitoring:
- Server crashes, nobody knows for hours
- Performance degrades gradually, unnoticed
- Debugging requires SSH into every server
- No historical data for capacity planning

CloudWatch provides **unified monitoring, logging, and alerting**.

### Metrics Deep Dive

**Default EC2 Metrics (free, 5-minute granularity):**
- CPUUtilization
- NetworkIn/NetworkOut
- DiskReadOps/DiskWriteOps
- StatusCheckFailed

**CloudWatch Agent Metrics (custom namespace):**
- mem_used_percent (memory - not available by default!)
- disk_used_percent
- cpu_usage_user/system/idle
- netstat metrics

**Why Memory Isn't Default:**
EC2 hypervisor can see CPU/network/disk from outside the VM. Memory usage requires an agent inside the VM.

### Alarms Explained

**Anatomy of an Alarm:**
```
Alarm: DantePlanner-HighCPU
├── Metric: cpu_usage_user
├── Namespace: DantePlanner
├── Statistic: Average
├── Period: 300 seconds
├── Threshold: > 70
├── Evaluation Periods: 1
├── Actions:
│   ├── ALARM → SNS:danteplanner-alerts
│   └── OK → SNS:danteplanner-alerts
└── Treat Missing Data: notBreaching
```

**Alarm States:**
```
         threshold crossed
    OK ─────────────────────→ ALARM
    ↑                            │
    │      below threshold       │
    └────────────────────────────┘

    INSUFFICIENT_DATA
    (no data points received)
```

**Treat Missing Data Options:**
| Setting | Behavior |
|---------|----------|
| missing | Maintain current state |
| notBreaching | Treat as OK |
| breaching | Treat as ALARM |
| ignore | Don't evaluate |

### Log Insights Query Language

```sql
-- Find errors with context
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 100

-- Count errors by type
fields @message
| filter @message like /Exception/
| parse @message /(?<exception>\w+Exception)/
| stats count() by exception

-- P99 latency from access logs
fields @timestamp, @message
| parse @message /"(?<method>\w+) (?<path>[^ ]+)" (?<status>\d+) .* (?<latency>\d+)ms/
| stats percentile(latency, 99) as p99 by bin(5m)
```

### Metric Filters

Extract metrics from logs:
```
Log Group: /ecs/danteplanner/nginx
Filter Pattern: [ip, id, user, timestamp, request, status=5*, size]
    ↓
Creates metric: HTTP5xxCount in namespace DantePlanner
    ↓
Create alarm on this metric
```

**Filter Pattern Syntax:**
- `[a, b, c]` - Space-delimited fields
- `status=5*` - Field "status" starts with "5"
- `"ERROR"` - Literal string match

---

## ECR (Elastic Container Registry)

### What Problem Does It Solve?
Docker Hub problems:
- Rate limits (100 pulls/6 hours for anonymous)
- Public images by default
- Not integrated with AWS IAM
- Data transfer costs from outside AWS

ECR provides **private Docker registry in your AWS account**.

### How It Works

```
1. Create Repository
   aws ecr create-repository --repository-name danteplanner-backend

2. Authenticate Docker
   aws ecr get-login-password | docker login --username AWS --password-stdin \
     123456789.dkr.ecr.us-west-2.amazonaws.com

3. Tag Image
   docker tag myapp:latest 123456789.dkr.ecr.us-west-2.amazonaws.com/myapp:v1.0

4. Push
   docker push 123456789.dkr.ecr.us-west-2.amazonaws.com/myapp:v1.0

5. Pull (from EC2)
   docker pull 123456789.dkr.ecr.us-west-2.amazonaws.com/myapp:v1.0
```

### Image Lifecycle Policies

Automatically clean old images:
```json
{
  "rules": [
    {
      "rulePriority": 1,
      "selection": {
        "tagStatus": "untagged",
        "countType": "sinceImagePushed",
        "countNumber": 7,
        "countUnit": "days"
      },
      "action": {"type": "expire"}
    }
  ]
}
```

### Image Scanning

ECR can scan images for vulnerabilities:
```
Push image → ECR scans → Reports CVEs
                          ├── Critical: Log4Shell
                          ├── High: OpenSSL buffer overflow
                          └── Medium: ...
```

Enable with: `aws ecr put-image-scanning-configuration --scan-on-push true`

---

## IAM (Identity and Access Management)

### What Problem Does It Solve?
Without IAM:
- Single root account for everything
- Share passwords to grant access
- No audit trail
- All-or-nothing permissions

IAM provides **granular access control with full audit trail**.

### Identity Types

```
IAM User (human)
├── Has username/password (console)
├── Has access keys (CLI/API)
└── Permanent credentials

IAM Role (machine/temporary)
├── No permanent credentials
├── Assumed by: EC2, Lambda, users, other accounts
└── Temporary credentials (auto-rotated)

IAM Group (collection)
├── Contains users
└── Policies attached to group apply to all members
```

### Policy Evaluation Logic

```
         ┌─────────────────────┐
         │ Is there explicit   │
         │ Deny?               │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
    Yes  │                     │  No
   ◄─────┤      DENIED         │─────►┐
         │                     │      │
         └─────────────────────┘      │
                                      │
         ┌────────────────────────────▼─┐
         │ Is there explicit Allow?     │
         └──────────┬───────────────────┘
                    │
         ┌──────────▼──────────┐
    Yes  │                     │  No
   ◄─────┤     ALLOWED         │─────►─────► DENIED
         │                     │             (implicit)
         └─────────────────────┘
```

**Key Rule:** Explicit Deny always wins.

### Common Permission Boundaries

**Problem:** Admin creates user, user escalates to admin.
**Solution:** Permission boundaries limit maximum permissions.

```
User Policy: Allow s3:*, ec2:*, iam:*
Permission Boundary: Allow s3:*, ec2:*
Effective: Allow s3:*, ec2:* (iam denied by boundary)
```

### Assuming Roles

```
GitHub Actions (AWS_ACCESS_KEY_ID)
    │
    │ aws sts assume-role --role-arn arn:aws:iam::123:role/DeployRole
    ▼
Temporary Credentials (15min-12hr)
    │
    │ Use for deployment
    ▼
Actions performed as DeployRole
```

**Trust Policy (who can assume):**
```json
{
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"AWS": "arn:aws:iam::123:user/github-actions"},
    "Action": "sts:AssumeRole"
  }]
}
```

---

## SNS (Simple Notification Service)

### What Problem Does It Solve?
Without SNS:
- Alarm fires, must poll to check
- Sending to multiple destinations requires custom code
- No fan-out capability

SNS provides **pub/sub messaging with multiple delivery protocols**.

### How It Works

```
Publisher (CloudWatch Alarm)
    │
    │ Publish to topic
    ▼
SNS Topic (danteplanner-alerts)
    │
    ├──→ Email subscriber → inbox
    ├──→ SMS subscriber → phone
    ├──→ Lambda subscriber → function invoked
    ├──→ SQS subscriber → message queued
    └──→ HTTP subscriber → webhook called
```

### Message Filtering

Subscribe to only certain messages:
```json
{
  "severity": ["critical", "high"]
}
```

Only messages with matching attributes delivered to this subscriber.

### Delivery Retry

SNS retries failed deliveries:
- HTTP: 3 retries, exponential backoff
- Email: No retry (fire and forget)
- SQS: Retry until success
- Lambda: 3 retries

---

## Service Integration Summary

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           GitHub Actions                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ 1. Build Docker image                                                │ │
│  │ 2. Push to ECR                                                       │ │
│  │ 3. Send deploy command via SSM                                       │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
                   ┌─────────────────┼─────────────────┐
                   ▼                 ▼                 ▼
             ┌─────────┐       ┌─────────┐       ┌─────────┐
             │   ECR   │       │   SSM   │       │   IAM   │
             │         │       │         │       │         │
             │ Stores  │       │ Stores  │       │ Grants  │
             │ Docker  │       │ secrets │       │ permis- │
             │ images  │       │ in Para-│       │ sions   │
             │         │       │ meter   │       │ to all  │
             │         │       │ Store   │       │ services│
             └────┬────┘       └────┬────┘       └────┬────┘
                  │                 │                  │
                  └─────────────────┼──────────────────┘
                                    ▼
                            ┌──────────────┐
                            │     EC2      │
                            │              │
                            │ Pulls images │
                            │ from ECR     │
                            │              │
                            │ Fetches      │
                            │ secrets from │
                            │ SSM          │
                            │              │
                            │ Runs Docker  │
                            │ containers   │
                            └──────┬───────┘
                                   │
                   ┌───────────────┼───────────────┐
                   ▼               ▼               ▼
             ┌──────────┐   ┌──────────┐   ┌──────────┐
             │CloudWatch│   │    S3    │   │   SNS    │
             │          │   │          │   │          │
             │ Receives │   │ Stores   │   │ Sends    │
             │ logs via │   │ database │   │ alarm    │
             │ awslogs  │   │ backups  │   │ notifi-  │
             │ driver   │   │          │   │ cations  │
             │          │   │          │   │          │
             │ Triggers │   │          │   │          │
             │ alarms   │───┼──────────┼───│          │
             └──────────┘   └──────────┘   └──────────┘
```
