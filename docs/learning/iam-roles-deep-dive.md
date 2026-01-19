# IAM Roles Deep Dive: From Basics to Internals

How IAM roles actually work, how EC2 instances get credentials, and the policy evaluation system.

---

## The Fundamental Problem

Before IAM roles, deploying applications on EC2 meant:

```
Developer creates AWS access keys
    ↓
Embeds keys in application code or config file
    ↓
Keys are stored on EC2 instance disk
    ↓
Keys never expire, never rotate
    ↓
If instance compromised → keys stolen → full AWS access
```

**The nightmare scenario:**
- Keys leaked in Git commit
- Keys found in /var/log by attacker
- Keys shared across environments
- No way to audit which instance did what

---

## What Is an IAM Role?

An IAM role is an **identity without permanent credentials**.

### Users vs Roles

| Aspect | IAM User | IAM Role |
|--------|----------|----------|
| Credentials | Permanent (access key + secret) | Temporary (auto-rotated) |
| Who uses it | Humans | Machines (EC2, Lambda, etc.) |
| Authentication | You prove who you are | You prove you're allowed to assume |
| Expiration | Never (unless manually rotated) | 15 minutes to 12 hours |

### The Key Insight

A role is a **permission set that can be temporarily worn** by something else.

```
IAM Role: "danteplanner-ec2-role"
    │
    │ Can be assumed by:
    │   - EC2 instances in my account
    │   - Lambda functions in my account
    │   - Users who need temporary elevated access
    │
    └── Grants permissions:
        - Read from S3
        - Write to CloudWatch
        - Read SSM parameters
```

---

## How EC2 Gets Role Credentials

### The Instance Metadata Service (IMDS)

Every EC2 instance has access to a special IP address: `169.254.169.254`

This is the **Instance Metadata Service** - a local HTTP endpoint that provides information about the instance.

```
┌─────────────────────────────────────────────────────────┐
│                     EC2 Instance                         │
│                                                          │
│   Your Application                                       │
│        │                                                 │
│        │ curl http://169.254.169.254/...                │
│        ▼                                                 │
│   ┌─────────────────────────────────────────────────┐   │
│   │         Instance Metadata Service (IMDS)         │   │
│   │                                                   │   │
│   │  /latest/meta-data/                              │   │
│   │    ├── instance-id          → i-0abc123def       │   │
│   │    ├── instance-type        → t3.small           │   │
│   │    ├── local-ipv4           → 10.0.1.50          │   │
│   │    ├── placement/region     → us-west-2          │   │
│   │    └── iam/                                      │   │
│   │         └── security-credentials/                │   │
│   │              └── danteplanner-ec2-role           │   │
│   │                   ├── AccessKeyId                │   │
│   │                   ├── SecretAccessKey            │   │
│   │                   ├── Token                      │   │
│   │                   └── Expiration                 │   │
│   └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### The Credential Flow

```
1. EC2 Instance launches with IAM Role attached
         │
         ▼
2. AWS STS generates temporary credentials for the role
         │
         ▼
3. Credentials available via IMDS at 169.254.169.254
         │
         ▼
4. AWS SDK automatically fetches credentials from IMDS
         │
         ▼
5. SDK uses credentials to sign API requests
         │
         ▼
6. Credentials expire (typically 6 hours)
         │
         ▼
7. SDK automatically refreshes from IMDS (go to step 2)
```

### Fetching Credentials Manually

```bash
# Step 1: Get role name
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/
# Returns: danteplanner-ec2-role

# Step 2: Get credentials for that role
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/danteplanner-ec2-role

# Returns:
{
  "Code": "Success",
  "LastUpdated": "2024-01-20T10:30:00Z",
  "Type": "AWS-HMAC",
  "AccessKeyId": "ASIA...",
  "SecretAccessKey": "wJalr...",
  "Token": "IQoJb3...",
  "Expiration": "2024-01-20T16:30:00Z"
}
```

### IMDSv2: Token-Based Security

IMDSv1 had a vulnerability: SSRF attacks could steal credentials.

```
Attacker's malicious input
    ↓
Application makes request to http://169.254.169.254/...
    ↓
Credentials leaked to attacker
```

IMDSv2 adds a token requirement:

```bash
# Step 1: Get session token (requires PUT with header)
TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")

# Step 2: Use token in subsequent requests
curl -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/iam/security-credentials/
```

**Why this helps:** Most SSRF vulnerabilities use GET requests or can't set custom headers. The PUT + header requirement blocks most attacks.

---

## The Two Types of Policies

Every IAM role has two policy types:

### 1. Trust Policy (Who Can Assume)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

This says: "EC2 service can assume this role."

**Principal types:**
| Principal | Example | Use Case |
|-----------|---------|----------|
| Service | `ec2.amazonaws.com` | EC2 instances |
| Service | `lambda.amazonaws.com` | Lambda functions |
| AWS Account | `arn:aws:iam::123456789:root` | Cross-account access |
| IAM User | `arn:aws:iam::123456789:user/admin` | User role switching |
| Federated | `arn:aws:iam::123456789:saml-provider/Okta` | SSO users |

### 2. Permission Policy (What Can Be Done)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::my-bucket/*"
    }
  ]
}
```

This says: "Whoever assumes this role can read/write to my-bucket."

### The Relationship

```
Trust Policy                    Permission Policy
"WHO can wear this hat"         "WHAT the hat-wearer can do"
         │                               │
         │                               │
         └───────────┬───────────────────┘
                     │
                     ▼
              IAM Role
         "danteplanner-ec2-role"
                     │
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
   EC2 can assume          Can access S3,
   this role               CloudWatch, SSM
```

---

## Policy Evaluation: How AWS Decides Allow/Deny

### The Evaluation Algorithm

```
                    ┌────────────────────┐
                    │ Request comes in   │
                    └─────────┬──────────┘
                              │
                    ┌─────────▼──────────┐
                    │ Gather all         │
                    │ applicable policies│
                    └─────────┬──────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
   Identity-based      Resource-based       Permission
   policies            policies             boundaries
   (attached to        (attached to         (limits on
    user/role)          resource)            identity)
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │ Any explicit DENY? │
                    └─────────┬──────────┘
                              │
              ┌───────────────┴───────────────┐
              │ Yes                           │ No
              ▼                               ▼
        ┌──────────┐              ┌─────────────────┐
        │  DENIED  │              │ Any explicit    │
        └──────────┘              │ ALLOW?          │
                                  └────────┬────────┘
                                           │
                               ┌───────────┴───────────┐
                               │ Yes                   │ No
                               ▼                       ▼
                         ┌──────────┐           ┌──────────┐
                         │ ALLOWED  │           │  DENIED  │
                         └──────────┘           │(implicit)│
                                                └──────────┘
```

### Key Rules

1. **Default deny:** Everything is denied unless explicitly allowed
2. **Explicit deny wins:** A deny overrides any allow
3. **All applicable policies evaluated:** Not just the first match

### Example Evaluation

**Scenario:** EC2 instance tries to write to S3 bucket

```
EC2 Instance (with role: danteplanner-ec2-role)
    │
    │ aws s3 cp file.txt s3://my-bucket/
    ▼
AWS evaluates:

1. Role's permission policy:
   {
     "Effect": "Allow",
     "Action": "s3:PutObject",
     "Resource": "arn:aws:s3:::my-bucket/*"
   }
   → ALLOW

2. Bucket policy (if any):
   {
     "Effect": "Allow",
     "Principal": "*",
     "Action": "s3:PutObject",
     "Resource": "arn:aws:s3:::my-bucket/*"
   }
   → ALLOW

3. Permission boundary (if any):
   {
     "Effect": "Allow",
     "Action": "s3:*",
     "Resource": "*"
   }
   → ALLOW

4. Service control policy (if in organization):
   No relevant policy
   → N/A

Final decision: ALLOW (no explicit deny, at least one allow)
```

---

## How Role Attachment Works (Network Level)

### When You Launch EC2 with a Role

```
You: aws ec2 run-instances --iam-instance-profile danteplanner-profile
                                        │
                                        │
                    ┌───────────────────▼───────────────────┐
                    │          AWS EC2 Service              │
                    │                                       │
                    │  1. Validate instance profile exists  │
                    │  2. Validate trust policy allows EC2  │
                    │  3. Launch instance                   │
                    │  4. Associate profile with instance   │
                    └───────────────────┬───────────────────┘
                                        │
                    ┌───────────────────▼───────────────────┐
                    │          AWS STS Service              │
                    │                                       │
                    │  1. Generate temporary credentials    │
                    │  2. Set expiration (default 6 hours)  │
                    │  3. Make available via IMDS           │
                    └───────────────────┬───────────────────┘
                                        │
                    ┌───────────────────▼───────────────────┐
                    │         EC2 Instance (running)        │
                    │                                       │
                    │  IMDS now serves credentials at       │
                    │  169.254.169.254                      │
                    └───────────────────────────────────────┘
```

### Instance Profile vs Role

An **Instance Profile** is a container for a role that can be attached to EC2:

```
IAM Role: "danteplanner-ec2-role"
    │
    └── Referenced by
            │
            ▼
Instance Profile: "danteplanner-profile"
    │
    └── Attached to
            │
            ▼
EC2 Instance: i-0abc123def
```

**Why the extra layer?**
- Historical: Roles were added after EC2 launched
- One profile per role (usually same name)
- AWS Console creates both automatically
- CLI/SDK need explicit profile creation

### The 169.254.169.254 Magic

This IP is a **link-local address** - it only exists within the instance.

```
┌─────────────────────────────────────────────────────────────┐
│                        AWS VPC                               │
│                                                              │
│   ┌─────────────────────┐       ┌─────────────────────┐     │
│   │    EC2 Instance     │       │    EC2 Instance     │     │
│   │                     │       │                     │     │
│   │  169.254.169.254 ─┐ │       │  169.254.169.254 ─┐ │     │
│   │  (only visible    │ │       │  (only visible    │ │     │
│   │   inside THIS     │ │       │   inside THIS     │ │     │
│   │   instance)       │ │       │   instance)       │ │     │
│   │                   │ │       │                   │ │     │
│   │         ┌─────────┘ │       │         ┌─────────┘ │     │
│   │         ▼           │       │         ▼           │     │
│   │   Returns creds    │       │   Returns creds     │     │
│   │   for Role A       │       │   for Role B        │     │
│   └─────────────────────┘       └─────────────────────┘     │
│                                                              │
│   Instances can't reach each other's 169.254.169.254        │
│   Each gets their own isolated IMDS                         │
└─────────────────────────────────────────────────────────────┘
```

### Network Path (Packet Level)

```
Application makes request:
    curl http://169.254.169.254/latest/meta-data/

    ┌──────────────────────────────────────────────────────┐
    │                    EC2 Instance                       │
    │                                                       │
    │   Application                                         │
    │       │                                               │
    │       │ HTTP GET to 169.254.169.254                   │
    │       ▼                                               │
    │   Linux Network Stack                                 │
    │       │                                               │
    │       │ Routing: 169.254.0.0/16 → local              │
    │       ▼                                               │
    │   Hypervisor intercepts                              │
    │       │                                               │
    │       │ Routes to AWS backend                        │
    │       ▼                                               │
    │   AWS IMDS Backend                                    │
    │       │                                               │
    │       │ Looks up: Which role is attached             │
    │       │           to this instance?                  │
    │       ▼                                               │
    │   Returns credentials (or 404 if no role)            │
    └──────────────────────────────────────────────────────┘
```

The request never leaves the physical host - the hypervisor intercepts it.

---

## AWS SDK Credential Chain

When you use AWS SDK without explicit credentials, it searches in order:

```
AWS SDK needs credentials
         │
         ▼
1. Environment variables?
   AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
   └── Found? Use them. Not found? Continue.
         │
         ▼
2. Shared credentials file?
   ~/.aws/credentials
   └── Found? Use them. Not found? Continue.
         │
         ▼
3. EC2 Instance Profile (IMDS)?
   curl http://169.254.169.254/.../security-credentials/
   └── Found? Use them. Not found? Continue.
         │
         ▼
4. ECS container credentials?
   (If running in ECS)
   └── Found? Use them. Not found? Continue.
         │
         ▼
5. No credentials found
   └── Raise error
```

**This is why EC2 with IAM roles "just works"** - the SDK automatically finds credentials from IMDS.

---

## Cross-Account Role Assumption

### The Scenario

```
Account A (123456789)              Account B (987654321)
┌──────────────────────┐          ┌──────────────────────┐
│                      │          │                      │
│  EC2 Instance        │          │  S3 Bucket           │
│  (danteplanner-role) │ ──────── │  (backup-bucket)     │
│                      │   needs  │                      │
│                      │  access  │                      │
└──────────────────────┘          └──────────────────────┘
```

### The Solution: Role Chaining

```
Account B creates role: "CrossAccountS3Access"
    │
    │ Trust policy:
    │ {
    │   "Principal": {
    │     "AWS": "arn:aws:iam::123456789:role/danteplanner-role"
    │   },
    │   "Action": "sts:AssumeRole"
    │ }
    │
    │ Permission policy:
    │ {
    │   "Action": "s3:*",
    │   "Resource": "arn:aws:s3:::backup-bucket/*"
    │ }
    │
    ▼
Account A's EC2 can now assume Account B's role
```

### The Flow

```
EC2 in Account A
    │
    │ 1. Gets own credentials from IMDS (danteplanner-role)
    │
    │ 2. aws sts assume-role \
    │      --role-arn arn:aws:iam::987654321:role/CrossAccountS3Access
    │
    ▼
AWS STS
    │
    │ 3. Validates:
    │    - Does Account A's role have sts:AssumeRole permission?
    │    - Does Account B's role trust Account A's role?
    │
    │ 4. Issues temporary credentials for Account B's role
    │
    ▼
EC2 now has TWO sets of credentials:
    - Own role (for Account A resources)
    - Assumed role (for Account B resources)
```

---

## Managed Policies vs Inline Policies

### Managed Policies

```
AWS Managed Policy: "AmazonS3ReadOnlyAccess"
    │
    ├── Attached to: User A
    ├── Attached to: User B
    ├── Attached to: Role X
    └── Attached to: Role Y

Changes to policy affect all attached identities
```

**Types:**
- **AWS Managed:** Created by AWS, updated automatically
- **Customer Managed:** Created by you, you control updates

### Inline Policies

```
Role: "danteplanner-ec2-role"
    │
    └── Inline Policy (embedded):
        {
          "Action": "s3:GetObject",
          "Resource": "arn:aws:s3:::my-specific-bucket/*"
        }

This policy exists ONLY on this role
Deleted when role is deleted
```

### When to Use Which

| Use Case | Policy Type |
|----------|-------------|
| Standard permissions (S3 read, EC2 admin) | AWS Managed |
| Company-wide custom permissions | Customer Managed |
| Role-specific, non-reusable | Inline |
| Strict 1:1 relationship | Inline |

---

## Permission Boundaries

### The Problem

Admin creates role with AdministratorAccess:
```
Admin User
    │
    │ Creates role with full admin
    ▼
New Role: "DeveloperRole"
    │
    │ AdministratorAccess policy
    ▼
Developer uses role → Has full admin → Deletes production database
```

### The Solution

Permission boundary limits the maximum permissions:

```
Role: "DeveloperRole"
    │
    ├── Permission Policy: AdministratorAccess (Allow *)
    │
    └── Permission Boundary: DeveloperBoundary
        {
          "Effect": "Allow",
          "Action": [
            "s3:*",
            "dynamodb:*",
            "lambda:*"
          ],
          "Resource": "*"
        }

Effective permissions = Intersection of both
    = Only S3, DynamoDB, Lambda (NOT EC2, IAM, etc.)
```

### Visual Representation

```
┌─────────────────────────────────────────────────────────┐
│                 Permission Boundary                      │
│                 (Maximum allowed)                        │
│                                                          │
│    ┌──────────────────────────────────────────────┐     │
│    │           Permission Policy                   │     │
│    │           (What's requested)                  │     │
│    │                                               │     │
│    │    ┌────────────────────────────────────┐    │     │
│    │    │                                    │    │     │
│    │    │     Effective Permissions          │    │     │
│    │    │     (Intersection)                 │    │     │
│    │    │                                    │    │     │
│    │    └────────────────────────────────────┘    │     │
│    └──────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘
```

---

## Service Control Policies (SCPs)

For AWS Organizations - limits what accounts can do:

```
AWS Organization
    │
    ├── Root
    │   └── SCP: "DenyDeleteProduction"
    │       {
    │         "Effect": "Deny",
    │         "Action": [
    │           "rds:DeleteDBInstance",
    │           "ec2:TerminateInstances"
    │         ],
    │         "Resource": "*",
    │         "Condition": {
    │           "StringEquals": {
    │             "aws:ResourceTag/Environment": "production"
    │           }
    │         }
    │       }
    │
    ├── OU: Production
    │   └── Account: 123456789
    │       └── Even admin users can't delete production resources
    │
    └── OU: Development
        └── Account: 987654321
            └── (Same restriction applies)
```

**SCPs don't grant permissions** - they only restrict.

---

## Debugging Permission Issues

### Common Errors and Causes

| Error | Likely Cause |
|-------|--------------|
| `AccessDenied` | Missing permission in policy |
| `UnauthorizedAccess` | Trust policy doesn't allow assumption |
| `ExpiredToken` | Credentials expired, SDK should auto-refresh |
| `InvalidIdentityToken` | Role assumption failed |

### Debugging Steps

```bash
# 1. Who am I?
aws sts get-caller-identity

# 2. What's my role?
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/

# 3. Simulate permission check
aws iam simulate-principal-policy \
  --policy-source-arn arn:aws:iam::123456789:role/my-role \
  --action-names s3:GetObject \
  --resource-arns arn:aws:s3:::my-bucket/file.txt

# 4. Check CloudTrail for denied events
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=GetObject \
  --max-results 10
```

### The IAM Policy Simulator

AWS Console → IAM → Policy Simulator

```
Select: Role "danteplanner-ec2-role"
Action: s3:PutObject
Resource: arn:aws:s3:::my-bucket/file.txt

Result:
├── Allowed by: danteplanner-s3-policy (explicit allow)
├── Not denied by: (no explicit denies)
└── Final: ALLOWED
```

---

## Best Practices Summary

1. **Never use long-term credentials on EC2** - Always use IAM roles

2. **Least privilege** - Only grant permissions actually needed

3. **Use conditions** - Restrict by IP, time, MFA, tags

4. **Enable IMDSv2** - Protect against SSRF attacks

5. **Use permission boundaries** - Limit maximum permissions for delegated admins

6. **Audit with CloudTrail** - Log all API calls for forensics

7. **Rotate regularly** - Roles auto-rotate, but review policies periodically

8. **Separate roles by function** - Don't reuse roles across different applications

9. **Tag roles** - For cost allocation and organization

10. **Use SCPs in Organizations** - Guardrails across all accounts
