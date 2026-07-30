---
status: Accepted
tracking: none
---

# 0001 AWS account topology

## Summary

Replace the single-account layout with an AWS Organization: an organizational-unit hierarchy, one
member account per workload, human access through short-lived identity-center sessions, and
preventive guardrails expressed as service control policies. Staging arrives first as a member
account created and destroyed around each verification run, and production follows once staging has
proven the procedure.

## Motivation

The schema-decomposition window needs a rehearsal against a restored production dump, and the
verification harness that gates it needs somewhere to run. Both want an environment that can hold a
copy of real user data and then be destroyed.

Today that environment would sit beside production in the same account, separated by a Terraform
state file and nothing else. `terraform/README.md` already names the guard that would otherwise
cover this and states its limit: `allowed_account_ids` fails a plan whose credentials resolve to
another account, which is a guarantee a workspace cannot provide, "since workspaces isolate state
and nothing else". In one account that guard cannot tell the two environments apart, so it degrades
to an assertion that is always true.

After this lands, an environment boundary is an account boundary. A credential for one environment
cannot reach another's resources, guardrails are enforced above the account rather than inside it,
and destroying an environment is a bounded operation with a verifiable end state.

## Current behavior

One account holds everything.

- `terraform/iam-bootstrap` runs on local state, because it creates the bucket every other stack
  stores state in. It also creates the provisioning role and the GitHub OIDC provider.
- Six stacks declare a partial `backend "s3"` and take the bucket at init time from
  `terraform/backend.hcl`. Each sets `workspace_key_prefix = "env"`, and no non-default workspace
  is in use.
- Every AWS provider declares `allowed_account_ids`, sourced from a required `aws_account_id`
  variable with no default.
- `terraform/backend.hcl.example` states that one file serves every stack, "because the bucket is
  shared".
- `modules/fleet/ecr.tf` computes the registry URL from account, region and repository name, and
  records that the repository is "created outside this stack" and never managed by it.
  `backend_ecr_account_id` exists, defaults to the caller's own account, and is unused.
- Human access is a single principal named in `trusted_admin_principal_arn`; continuous integration
  uses GitHub OIDC against the subject in `github_oidc_subject`.
- ADR 011 records that node public addressing is the egress path and that NAT gateways with private
  subnets were rejected as a deliberate cost decision, so an additional fleet adds no gateway cost.

## Prior art

AWS publishes the target shape twice. *Organizing Your AWS Environment Using Multiple Accounts*
gives the organizational-unit hierarchy (Security, Infrastructure, Workloads, Sandbox, Suspended)
and the rule that the management account runs no workload. The *Security Reference Architecture*
adds the two accounts that hierarchy exists to hold: a log archive nobody can delete from, and an
audit account that owns security tooling as a delegated administrator.

Control Tower is that guidance packaged as a managed service, and Account Factory for Terraform
wraps Control Tower's vending in the same tooling this repository already uses. Both are rejected
below, on cost model rather than on correctness.

Inside this repository the shape was anticipated twice already, which is the strongest evidence
available that it is the right one. `terraform/iam-bootstrap/terraform.tfvars.example` documents the
identity-center permission-set form of the trusted admin principal alongside the plain user and role
forms, and `backend_ecr_account_id` exists precisely so a fleet can pull images from a registry that
another account owns. Neither has ever been used.

## Proposal

An organization with all features enabled, so that policy types exist at all, carrying five
organizational units: Security, Infrastructure, Workloads (with production and non-production
beneath it), Sandbox, and Suspended.

Four member accounts. A log archive holding the organization trail, an audit account reserved for
security tooling, staging under non-production, and eventually production under production. The
management account keeps the organization, identity, and billing, and runs no workload.

Human access is a permission-set session scoped to one account and one role, expiring within the
hour. The organization access role each member account is created with is retained as a break-glass
path and is not a login path. Continuous integration keeps its existing federated exchange, scoped
per environment rather than per branch.

Guardrails are deny-only service control policies attached above the workload accounts: no root
principal actions, no leaving the organization, no disabling the trail, and no resources outside the
approved regions. An organization trail writes every account's API activity into the log archive,
where no principal outside that account may delete an object.

Each member account carries its own state bucket and provisioning role, so a backend configuration
names exactly one account. The backend indirection that currently holds one bucket becomes one
configuration per account, and the claim that a single file serves every stack no longer holds.

The image registry stays in one account and grants pull to the organization, which is what
`backend_ecr_account_id` was built to consume.

Staging exists only while a verification run needs it. Production migrates last, after staging has
executed the same procedure end to end.

## Decomposition

```
- organization — an organization with all features and the unit hierarchy, able to carry policy
- identity-center — every human path is a short-lived session scoped to one account (after: organization)
- security-accounts — a log archive that cannot be deleted from, and an audit account (after: organization)
- account-vending — the staging account, in the non-production unit (after: organization)
- per-account-bootstrap — state and a provisioning identity scoped to one account (after: account-vending, identity-center, security-accounts)
- guardrails — workload accounts constrained by policy, and their activity recorded (after: organization, per-account-bootstrap)
- registry-access — the backend image readable from every account in the organization (after: per-account-bootstrap)
- credential-retirement — no credential in any account outlives a session, or its exemption is written down (after: identity-center)
- staging-stacks — staging serving the application, and destroyable (after: per-account-bootstrap, registry-access)
- prod-migration — production running under the guardrails (after: staging-stacks, guardrails)
```

## Scenarios

### organization

```gherkin
Scenario: A unit accepts a preventive policy
  Given an organization created with all features enabled
  When an operator attaches a service control policy to the workloads unit
  Then the attachment succeeds and that policy is listed among the unit's attached policies
```

### identity-center

```gherkin
Scenario: An operator reaches a member account through an expiring session
  Given the operator holds an administrator permission set on the staging account
  When the operator signs in and asks which identity the session carries
  Then the answer names the staging account, and the credentials expire within 1 hour

Scenario: A newly vended account is assignable without rebuilding identity
  Given identity is enabled for the organization
  When a member account is added to the organization
  Then that account is available as an assignment target, and no existing permission set
       has to be recreated
```

### security-accounts

```gherkin
Scenario: A security account is placed in the security unit at creation
  Given the organization carries a security unit
  When the log archive account is created
  Then its parent is the security unit and its status is active

Scenario: The log archive refuses deletion from outside itself
  Given the log archive account holds the organization trail's destination
  When any principal outside that account attempts to delete an object under the trail prefix
  Then the request is denied
```

### guardrails

```gherkin
Scenario: A workload account is refused a resource outside the approved regions
  Given the staging account sits under the workloads unit
  When a principal in that account creates a compute instance in a region outside the approved set
  Then the request is denied by an explicit deny

Scenario: A workload account cannot remove itself from the organization
  Given the staging account sits under the workloads unit
  When a principal in that account requests to leave the organization
  Then the request is denied

Scenario: Workload guardrails do not constrain the management account
  Given a policy denying every region outside the approved set is attached at the root
  When a principal in the management account creates a resource in a region outside that set
  Then the request succeeds

Scenario: Member account activity is recorded where the member cannot reach it
  Given an organization trail writes to the log archive account
  When a principal in the staging account creates a resource
  Then a corresponding event is readable in the log archive under that account's prefix
```

### account-vending

```gherkin
Scenario: The staging account is created under the non-production unit
  Given the organization carries a non-production unit beneath workloads
  When the staging account is vended
  Then its parent is the non-production unit and its status is active

Scenario: Removing the account from configuration does not close it
  Given the staging account exists and is managed as code
  When its declaration is removed from configuration
  Then the account remains active and is not scheduled for closure
```

### per-account-bootstrap

```gherkin
Scenario: State for one account never lands in another's bucket
  Given the staging account has been bootstrapped
  When an operator initializes a stack against the staging backend configuration and applies it
  Then the state object is written in the staging account's bucket, and the production account's
       bucket is byte-identical to before

Scenario: A plan carrying the wrong account's credentials fails before reading any resource
  Given a stack configured for the staging account
  When an operator runs a plan with production credentials
  Then the plan exits non-zero reporting an account mismatch, and no resource is refreshed

Scenario: The provisioning identity trusts a permission set, not a standing user
  Given the staging account is reachable through an administrator permission set
  When an operator assumes the provisioning identity from that session
  Then the assumption succeeds, and the same assumption from a principal outside that
       permission set is denied

Scenario: Continuous integration reaches a member account without a stored key
  Given the staging environment is configured in the repository
  When a workflow job running in that environment requests credentials for the staging account
  Then it receives credentials expiring within 1 hour, and the same workflow running outside
       that environment is denied
```

### registry-access

```gherkin
Scenario: A member account pulls the backend image from the shared registry
  Given the registry grants pull to the organization
  When a node in the staging account pulls the backend image by tag
  Then the pull succeeds

Scenario: An account outside the organization is refused a pull
  Given the registry grants pull to the organization
  When a principal in an account outside the organization pulls the backend image
  Then the request is denied
```

### credential-retirement

```gherkin
Scenario: No account carries a credential that outlives a session
  Given every human path is a permission-set session and every automated path a federated role
  When an account's credential report is read
  Then it lists no access key in an active state

Scenario: An integration that cannot federate is exempted in writing, not silently
  Given a third party whose provider offers no way to present a token or assume a role
  When the credential inventory is reviewed
  Then that credential appears with a recorded exemption naming what blocks it, rather than as
       an unexplained failure of the invariant

Scenario: A credential is unreferenced before it is deleted
  Given a credential named in a trust policy or in a managed configuration
  When it is removed from every such reference and the change is applied
  Then deleting it afterwards leaves no policy naming a principal that no longer resolves
```

### staging-stacks

```gherkin
Scenario: Staging serves the application
  Given the staging stacks have been applied
  When a request reaches staging's entry point over HTTPS
  Then the backend reports itself healthy

Scenario: Staging never reaches production data
  Given staging has been applied with its own inputs
  When the staging fleet resolves the database it will write to
  Then it resolves to a database in the staging account, and no peering exists between the
       staging fleet and the production database network

Scenario: Destroying staging leaves nothing billable
  Given staging has been applied and then destroyed
  When the account's resources are listed across the approved regions
  Then no compute, database, or load-balancing resource remains
```

### prod-migration

```gherkin
Scenario: The procedure is rehearsed before production runs it
  Given the staging account was created by the same configuration as the production account
  When the migration procedure is executed against staging
  Then it completes without a step absent from the runbook

Scenario: Production runs under the workload guardrails
  Given production has been migrated to a member account under the production unit
  When a principal in that account attempts to disable the organization trail
  Then the request is denied

Scenario: The management account holds no workload
  Given production has been migrated
  When the management account's resources are listed
  Then it holds no compute, database, or application storage resource
```

## Invariants

- **No account identifier, role ARN, or trust relationship appears in a tracked file.** Gate: the
  ignore globs in `terraform/.gitignore`, plus a continuous-integration search of tracked files for
  twelve-digit sequences and ARN literals.
- **Every AWS provider declares the account it may apply into.** Gate: the account-mismatch scenario
  above, plus a search asserting no provider block omits it.
- **No account holds a long-lived access key.** Gate: a per-account credential report, asserted
  empty of active keys.
- **A backend configuration names exactly one account's bucket.** Gate: the state-isolation scenario
  above.
- **Every guardrail is deny-only.** Gate: no attached policy grants a permission, so removing every
  policy can only widen access, never break a working path.

## Decisions

- @accounts @isolation — an environment boundary is an account boundary, because the only guard this
  repository has against applying to the wrong place is a provider-level account assertion, and that
  assertion is vacuous when two environments share an account. REJECTED: one account with a staging
  workspace — workspaces isolate state and nothing else, so the account guard cannot distinguish the
  environments, and a network identifier supplied as a plain string can point staging at production's
  database network with nothing in the type system to stop it.
- @management-account @scp — production migrates out of the management account, because service
  control policies never apply to the management account and no override exists. REJECTED: leaving
  production where it is and guarding it some other way — every preventive control would be
  permanently void for the one workload that carries user data, which makes the guardrail layer a
  claim rather than a mechanism.
- @sequencing @rehearsal — staging lands before the production migration, because staging is the
  rehearsal ground for it. REJECTED: migrating production first, on the argument that it is the
  larger risk and should be retired earliest — doing it first means rehearsing nothing, and the
  procedure would be executed once, against the account that cannot afford a second attempt.
- @landing-zone @tooling @cost — the landing zone is self-managed in Terraform. REJECTED: Control
  Tower — it turns on configuration recording across the organization and bills per recorded
  configuration item, which inverts the cost model of an environment deliberately designed to create
  and destroy its whole resource set on every verification run, and it is materially harder to
  un-adopt than to adopt. REJECTED: Account Factory for Terraform — it inherits that cost model and
  adds a pipeline this repository has no other use for.
- @identity @sessions — human access is a permission-set session, and the organization access role
  created with each member account is break-glass only. REJECTED: using that role as the daily path
  — it is standing administrator access with no per-account scoping, so every routine action would
  carry the blast radius reserved for recovering from a broken identity plane.
- @staging @lifecycle — staging is created and destroyed around each verification run. REJECTED: a
  standing staging environment — it pays continuously for capacity used in bursts, and it retires
  the rebuild-from-scratch proof that a create-path defect would otherwise surface on every run.
- @security-accounts @tamper-resistance — the organization trail's destination lives in an account
  whose only purpose is holding it. REJECTED: the trail writing into the management account — the
  account with the broadest reach would then own the record of its own actions, so the record proves
  nothing against exactly the compromise it exists to detect.
- @state @accounts @secrets — Terraform state lives in the account whose resources it describes, so
  the bucket count tracks the account count and no account reads another's state. Terraform
  serializes every attribute it manages, including the ones providers mark sensitive, which makes a
  state file a secrets store whether or not anyone intended one. REJECTED: a single bucket in the
  management account with member stacks assuming a role inward — it deletes a bucket per account and
  restores the one backend configuration this proposal otherwise gives up, but it gathers every
  environment's secrets into the account service control policies cannot constrain, and it hands a
  staging apply write access to production's state. REJECTED: one bucket partitioned by key prefix —
  a prefix is a convention rather than a boundary, so a misconfigured one is a silent
  cross-environment write where a wrong account fails at the first call.

## Drawbacks

Every invocation now names an account explicitly, so "applied to the wrong account" is replaced by
"forgot to name the account". The new failure is louder and it will be more frequent.

The backend indirection loses the property it was built for. One file naming one shared bucket
becomes one file per account, so switching environments means reinitializing, and the convenience
documented in `backend.hcl.example` is gone rather than relocated.

Four accounts need budgets, alerting, and patching where one did. Account sprawl is a real recurring
cost and it does not shrink when the work is finished.

The registry becomes a cross-account dependency, so its availability and its policy are now shared
concerns rather than local ones.

Ephemeral staging pays fleet boot and reconciliation latency on every run, which lengthens the
feedback loop for anything iterating on the harness itself.

## Non-goals

- Threat detection and posture management across the organization. The audit account is created and
  left empty; nothing is delegated to it here.
- Configuration recording across the organization, which is the cost the tooling decision rejects.
- Control Tower, now or as a later migration target.
- Moving the edge or observability control planes into the account model; both stay where ADR 011
  and ADR 012 put them.
- A third environment. Two is what the verification work needs.
- Guardrails must not be relied upon to constrain the management account, which the third guardrail
  scenario asserts negatively rather than leaving to prose.

## Risks and rollback

**Permanently unundoable.** Enabling the organization makes the current account the management
account, and there is no supported path to convert a management account into a member. If production
must eventually leave it, production migrates; the account itself never does.

**Closure has a waiting period.** A member account cannot be closed immediately, and its email stays
claimed while it waits, so a mistyped address is expensive to correct rather than merely annoying.

**A broken identity plane locks everyone out.** Detection is an operator unable to obtain a session.
Recovery is the organization access role assumed from the management account, which is why it is
retained rather than deleted.

**A guardrail can deny a legitimate action.** Detection is an explicit-deny error naming the policy.
Because every policy is deny-only, rollback is detaching one policy, and no working path depends on
a policy remaining attached.

Staging rolls back by destroying it and moving the account into the suspended unit. The production
migration rolls back through the database snapshot and a fleet rebuild, both procedures this
repository already exercises.

## Open questions

None.
