# Cloudflare state: one per environment

This runbook moves the production edge state of `terraform/cloudflare` from the prod bucket's
`prod-fleet` workspace to the prod bucket's `default` workspace, and deletes the prod bucket's
`prod-fleet` and `staging` workspaces. Staging stays where it is, in the staging bucket's
`default` workspace. Afterwards each environment has one state, in its own bucket, and no
command selects a workspace for this stack.

Run it once, from the main checkout's root, where the gitignored `terraform/backend.prod.hcl`,
`terraform/backend.staging.hcl`, `terraform/cloudflare/prod.tfvars` and
`terraform/cloudflare/staging.tfvars` live. Run it before the branch that drops the workspace
step from `terraform/cloudflare/README.md` and `scripts/ops/terraform-drift-check.sh` merges.

## What was found

Read on 2026-11-26:

| Bucket | Workspace | Serial | Instances | Contents |
|---|---|---|---|---|
| prod | `default` | 3 | 0 | five stale outputs, no resources; a lineage of its own |
| prod | `prod-fleet` | 8 | 10 | the production edge: both tunnels, their configs and token reads, the load balancer, its monitor and both pools |
| prod | `staging` | 10 | 9 | five DNS records, both Access policies, both tunnel configs |
| staging | `default` | 7 | 19 | the staging edge |

Each of the nine instances in the prod bucket's `staging` workspace has the same address and
the same Cloudflare id as an instance in the staging bucket's `default` workspace. It is a
leftover copy, and the staging bucket's copy is the one that is planned.

`prod-fleet` planned `No changes.` with `-var-file=prod.tfvars`.

## Before the move: a fresh init plans "create everything"

`ops tf plan cloudflare` selects no workspace. Without `TF_WORKSPACE` it plans whatever
workspace `.terraform/` in that checkout has selected, and a fresh init selects `default`. In
the prod bucket that workspace holds no resources, so the plan reads as creating the whole
edge:

```
Plan: 8 to add, 0 to change, 0 to destroy.
```

followed by a `!` apply line. Never apply that plan: it tries to create a second copy of the
production edge beside the live one, and the account's two-origin load-balancer limit is
already used by the live pools. Until step 6 below, any prod plan of this stack is valid only
against `prod-fleet`.

## Rules for every step

- Every command runs from the main checkout's root with `AWS_PROFILE` as shown.
- Every terraform command names its workspace with `TF_WORKSPACE=<name>` in front of it,
  `ops tf plan` included. That variable overrides the selection in
  `.terraform/environment`, so no step depends on what another step or an earlier session left
  selected. Never `export` it. Terraform prints `The active workspace is being overridden
  using the TF_WORKSPACE environment variable.`; that line is expected.
- A step whose output differs from the expected output is a stop. Each stop below says what to
  run and what is left where. Keep the shell and the backups, and report the output. Do not
  repair by hand beyond the stop's own commands.
- Compare states by content (resources and outputs), never by lineage or serial. Terraform
  stores a push under a lineage and serial of its own choosing, so those two fields do not
  identify a state.

## Step 1: back up the staging state

The staging init re-points the main checkout's `.terraform/` at the staging bucket. Step 2
points it back at the prod bucket.

```bash
umask 077
mkdir -p ~/.local/state/danteplanner/tf-state-backup
TS=$(date +%Y%m%d-%H%M%S); echo "$TS"
BK=~/.local/state/danteplanner/tf-state-backup
AWS_PROFILE=staging TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare init -reconfigure -input=false -backend-config=../backend.staging.hcl
AWS_PROFILE=staging TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare workspace list
AWS_PROFILE=staging TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state pull > "$BK/cloudflare.staging.default.$TS.tfstate"
jq -r '"serial=\(.serial) instances=\([.resources[].instances[]] | length)"' "$BK/cloudflare.staging.default.$TS.tfstate"
```

Expected: the init ends with `Terraform has been successfully initialized!`, the list is
`* default` alone, and the last line is `serial=7 instances=19`.

Stop if the list shows another workspace or the instance count is not 19. A higher serial is
fine. Nothing has been written to either bucket; run step 2's init to point the checkout back
at the prod bucket, and stop there.

## Step 2: back up the three prod states

```bash
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare init -reconfigure -input=false -backend-config=../backend.prod.hcl
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare workspace list
for ws in prod-fleet default staging; do
  AWS_PROFILE=prod TF_WORKSPACE=$ws scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state pull > "$BK/cloudflare.prod.$ws.$TS.tfstate" || echo "PULL FAILED: $ws"
done
for ws in prod-fleet default staging; do
  jq -r --arg ws "$ws" '"\($ws) serial=\(.serial) instances=\([.resources[].instances[]] | length)"' "$BK/cloudflare.prod.$ws.$TS.tfstate"
done
```

Expected: the list names `default`, `prod-fleet` and `staging`; no `PULL FAILED` line; then

```
prod-fleet serial=8 instances=10
default serial=3 instances=0
staging serial=10 instances=9
```

Record the `prod-fleet` serial and instance count; step 5 compares against this file, not
against the table above.

Stop if `default` has any instance, if `prod-fleet` does not have 10, or if the list names a
workspace not in the table. A higher `prod-fleet` serial means someone applied since
2026-11-26; it is fine when the count is still 10 and step 4 plans no changes. On a stop
nothing has been written: production is in `prod-fleet` as before.

## Step 3: confirm the prod staging workspace is a copy

```bash
ids() { jq -r '.resources[] | . as $r | .instances[] | "\($r.mode).\($r.type).\($r.name)[\(.index_key // "")] \(.attributes.id)"' "$1" | sort; }
comm -23 <(ids "$BK/cloudflare.prod.staging.$TS.tfstate") <(ids "$BK/cloudflare.staging.default.$TS.tfstate")
```

Expected: no output. Every address and id in the prod bucket's `staging` workspace is also in
the staging bucket.

Stop if any line prints: that instance is tracked only in the prod bucket's `staging`
workspace, and deleting the workspace in step 7 would leave it unmanaged. Nothing has been
written.

## Step 4: plan production where it is now

```bash
TF_WORKSPACE=prod-fleet .claude/skills/ops/bin/ops tf plan cloudflare -- -var-file=prod.tfvars
```

Expected: `No changes.` and no `!` apply line.

Stop if the plan has any change, and apply nothing. A change here is drift that exists before
the move, and it would make the plan in step 6 unable to show whether the move kept the state
whole. Nothing has been written.

## Step 5: copy the state into `default`

The push runs only when `state list` on `default` succeeds and prints nothing, in the same
command:

```bash
if listed=$(AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state list); then
  if [ -z "$listed" ]; then
    AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state push -force "$BK/cloudflare.prod.prod-fleet.$TS.tfstate" \
      && echo "pushed into default"
  else
    echo "STOP: default is not empty"; printf '%s\n' "$listed"
  fi
else
  echo "STOP: state list failed"
fi
diff <(jq -S '{resources, outputs}' "$BK/cloudflare.prod.prod-fleet.$TS.tfstate") \
     <(AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state pull | jq -S '{resources, outputs}') \
  && echo "default matches prod-fleet"
```

Expected: `pushed into default`, then `default matches prod-fleet`.

`-force` is needed because the `default` key already holds a state with no resources and a
lineage of its own. Without it the push is refused with
`cannot import state with lineage "…" over unrelated state with lineage "…"`. That state is
in the step 2 backup.

Ignore the `diff` when `pushed into default` did not print.

- `STOP: state list failed` or `STOP: default is not empty`: nothing was pushed. Do not run the
  restore. Production is in `prod-fleet` as before. A non-empty `default` means something
  wrote to it after step 2; report the listed addresses.
- No `STOP` line and no `pushed into default`: the push failed. Run the restore below; it puts
  back whatever the push may have written.
- `pushed into default` and the `diff` prints lines: run the restore below.

### Restore `default` (the stop action of steps 5 and 6)

```bash
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state push -force "$BK/cloudflare.prod.default.$TS.tfstate"
diff <(jq -S '{resources, outputs}' "$BK/cloudflare.prod.default.$TS.tfstate") \
     <(AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state pull | jq -S '{resources, outputs}') \
  && echo "default matches its step 2 backup"
```

Expected: `default matches its step 2 backup`.

What is left where after the restore: production is tracked by `prod-fleet` alone, untouched
since step 2; `default` holds no resources again, so a fresh init plans "create everything" as
described above; the prod bucket's `staging` workspace and the staging bucket are untouched.
The branch that drops the workspace step must not merge. If the restore's `diff` prints lines,
stop with nothing further: `default` then tracks production objects, and no plan or apply may
run against it until the difference is read.

## Step 6: plan production in `default`

```bash
TF_WORKSPACE=default .claude/skills/ops/bin/ops tf plan cloudflare -- -var-file=prod.tfvars
```

Expected: `No changes.` and no `!` apply line.

Stop if the plan has any change, apply nothing, and run the restore in step 5. `prod-fleet` is
untouched until step 7.

## Step 7: delete the two workspaces

```bash
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare workspace delete -force prod-fleet
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare workspace delete -force staging
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare workspace list
AWS_PROFILE=prod scripts/ops/terraform-run.sh -chdir=terraform/cloudflare workspace select default
```

Expected: `Deleted workspace "prod-fleet"!` and `Deleted workspace "staging"!`, each followed by
a warning that the workspace was non-empty; the list is `* default` alone; then
`Switched to workspace "default".` (or `already selected`). The last command leaves
`.terraform/environment` on the only workspace there is; until it runs, a command without
`TF_WORKSPACE` fails on the deleted selection.

`-force` is needed because both workspaces still track resources (10 and 9), and terraform
refuses with `Workspace is not empty` without it. Terraform refuses to delete the workspace in
use, which `TF_WORKSPACE=default` makes `default`. The delete removes the state object under
`env/<workspace>/` in the bucket and nothing in Cloudflare: production's objects are tracked by
`default` since step 5, and staging's by the staging bucket.

Stop if either delete fails, and apply nothing. A failed delete leaves its workspace as it was;
`default` already holds production, so production is tracked twice until the delete succeeds.
Report it rather than planning or applying in the workspace left behind.

## Step 8: plan again

```bash
TF_WORKSPACE=default .claude/skills/ops/bin/ops tf plan cloudflare -- -var-file=prod.tfvars
aws --profile prod s3 ls "s3://$(rg -o 'bucket\s*=\s*"([^"]+)"' -r '$1' terraform/backend.prod.hcl)/env/" --recursive | rg 'cloudflare'
```

Expected: `No changes.` and no `!` apply line; the listing prints nothing.

Stop if the plan has any change or the listing names an object under `env/` for
`cloudflare`, and apply nothing.

The main checkout's `terraform/cloudflare/.terraform/` now rests on the prod bucket's
`default` workspace, which is the only workspace there.

## Way back after step 7

There is no workspace to return to after step 7. If `default` is later found damaged, put the
step 2 copy of `prod-fleet` back into it and plan:

```bash
AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state push -force "$BK/cloudflare.prod.prod-fleet.$TS.tfstate"
diff <(jq -S '{resources, outputs}' "$BK/cloudflare.prod.prod-fleet.$TS.tfstate") \
     <(AWS_PROFILE=prod TF_WORKSPACE=default scripts/ops/terraform-run.sh -chdir=terraform/cloudflare state pull | jq -S '{resources, outputs}') \
  && echo "default matches prod-fleet"
TF_WORKSPACE=default .claude/skills/ops/bin/ops tf plan cloudflare -- -var-file=prod.tfvars
```

Set `BK` again and `TS` to the run's timestamp (the backup file names carry it). Expected:
`default matches prod-fleet`, then `No changes.`. A plan with changes means the edge moved
after the backup was taken; apply nothing and read the plan. Each later apply leaves the
backup further behind, so take a fresh `state pull` backup before the next apply instead of
relying on this one.
