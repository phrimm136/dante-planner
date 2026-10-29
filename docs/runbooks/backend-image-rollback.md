# Rolling back the backend image

Reverts a bad backend release on both regions through the normal deploy pipeline.
`.github/workflows/deploy-fleet.yml` has no rollback job. Every run builds the commit it started
for (`.github/workflows/deploy-fleet.yml:50`) and bumps both overlays to that commit's image
(`.github/workflows/deploy-fleet.yml:306-309`). ArgoCD in each region syncs automatically
(`deploy/argocd/root-app-oregon.yaml:21-24`, and the same lines in
`deploy/argocd/root-app-seoul.yaml`). The control plane sets the tracked revision at boot from the
Terraform variable `gitops_target_revision` (`terraform/modules/fleet/user-data/cp.sh.tftpl:173`).
That variable defaults to `main` (`terraform/modules/fleet/variables.tf:155`). The live value is in
each region's tfvars, which this repository does not hold. This runbook assumes the default. So a
rollback is always a new commit on `main`.

The one rollback path today is a code revert. The revert goes through a pull request, PR Gate, and
deploy-fleet, and deploy-fleet adds the surge node that keeps each region serving during the roll.

Prerequisites: a clone of this repository and the right to open and merge pull requests to `main`.

## The one ordering rule

> **Check the release for migrations before you revert**.
>
> **If the revert touches the migration directory, do not roll back**.

ADR 004 says a failed release with migrations halts. It does not revert the image
(`docs/adr/004-deploy-trigger-dispatch-only.md:7`). Flyway is forward-only and Hibernate does not
check the schema at boot. An image without a migration that already ran starts, passes its probes,
and then returns 500 on every endpoint whose tables the migration changed or removed.

A code revert stays safe when the reverted commits do not touch the migration directory. The new
image then keeps every migration, and PR Gate tests it before it ships. Step 4.4 checks this. If the
reverted commits touch the migration directory, stop. Fix forward with a new migration instead
(`backend/src/main/resources/db/migration/CLAUDE.md:3`).

## 1. Find the two image SHAs

```bash
git fetch origin main
git log --format='%H %s' -n 1 --grep='^chore(deploy): bump backend image to' origin/main -- deploy/overlays/oregon/kustomization.yaml
```

Every bump commit has the subject `chore(deploy): bump backend image to <sha> (Oregon+Seoul) [skip ci]`
(`.github/workflows/deploy-fleet.yml:323`). The anchored `--grep` filter drops hand edits to the same file and reverted bump commits.
The line names the bad image, and its first field is the bump commit. Record both values.

```bash
export BAD_SHA=<sha in the bump subject>
export BUMP_COMMIT=<first field of the line>
git log -1 --format=%s "$BUMP_COMMIT" | rg -F "$BAD_SHA"
git grep -n newTag origin/main -- deploy/overlays
```

The `rg` line prints the bump subject. If it prints nothing, `BUMP_COMMIT` is the wrong commit. Find
it again before you continue. If either `newTag` line shows a value other than `BAD_SHA`, stop. The
regions do not both run the image from the newest bump, and this runbook does not cover that state.

Check whether the bad deploy was itself a rollback.

```bash
git log -1 --format=%s "$BAD_SHA"
```

If the subject starts with `Revert "`, stop. The image before it is the one an earlier rollback
already removed, so it is known bad. Walk back the bump history by hand to find the last known-good
image before you continue.

The last good image is the `newTag` in the parent of the bump commit.

```bash
git show "$BUMP_COMMIT^:deploy/overlays/oregon/kustomization.yaml" | rg newTag
export GOOD_SHA=<sha that line prints>
```

## 2. Check the release for migrations

```bash
git diff --name-only --diff-filter=A "$GOOD_SHA" "$BAD_SHA" -- backend/src/main/resources/db/migration
```

PR Gate detects new migrations with a similar command (`.github/workflows/pr-gate.yml:159-160`). PR
Gate uses a three-dot diff from `origin/main` to the pull request head, limited to `*.sql` files.
This runbook uses a two-dot diff between the two images over the whole directory. If it prints a
file, the release added a migration. Keep that migration out of the revert, and let step 4.4 confirm
it. If it prints nothing, continue.

## 3. Do not use workflow_dispatch as a rollback

Dispatching `deploy-fleet.yml` on an older branch or tag does not roll production back:

- The trigger takes no inputs (`.github/workflows/deploy-fleet.yml:16`). The only choice is the ref.
- A dispatch builds the tip of the dispatched ref (`.github/workflows/deploy-fleet.yml:50`,
  `.github/workflows/deploy-fleet.yml:92-95`).
- `bump-tag` checks out the dispatched ref (`.github/workflows/deploy-fleet.yml:292`) and pushes
  the bump to that same ref with `git push origin "HEAD:${REF_NAME}"`
  (`.github/workflows/deploy-fleet.yml:313`, `.github/workflows/deploy-fleet.yml:324`).
- ArgoCD tracks `gitops_target_revision`, which defaults to `main`
  (`terraform/modules/fleet/user-data/cp.sh.tftpl:173`, `terraform/modules/fleet/variables.tf:155`).
  With the default, a bump on another ref never reaches the clusters.
- If the push succeeds, `settle-down` waits for an image that never syncs and fails
  (`.github/workflows/deploy-fleet.yml:376-384`). The failure leaves both surge nodes running on
  purpose (`.github/workflows/deploy-fleet.yml:326-330`).
- If the push is rejected, for example on a tag, `bump-tag` fails. `settle-down` needs `bump-tag`
  (`.github/workflows/deploy-fleet.yml:332`), so it does not run. `surge-up` already finished
  (`.github/workflows/deploy-fleet.yml:275`), so both surge nodes stay up in this case too.

A dispatch on `main` rebuilds the head of `main`, which still holds the bad code. Use it to retry a
failed deploy of `main`, not to roll back.

## 4. Revert the code

1. Create a branch from `origin/main`.
2. List the backend commits in the bad release.

   ```bash
   git log --oneline "$GOOD_SHA..$BAD_SHA" -- backend
   ```

3. Revert the commits that caused the failure with `git revert`.
4. Check that the revert does not touch the migration directory.

   ```bash
   git diff --name-only origin/main HEAD -- backend/src/main/resources/db/migration
   ```

   If it prints a file, stop. Fix forward with a new migration instead.
5. Open a pull request to `main`. Rulesets protect `main` (`.github/workflows/deploy-fleet.yml:284-285`).
6. Merge the pull request after PR Gate passes. Keep `[skip ci]` out of every commit message.
   The push to `main` must run PR Gate (`.github/workflows/pr-gate.yml:11-12`).
7. Wait for deploy-fleet. A green PR Gate run on `main` starts it
   (`.github/workflows/deploy-fleet.yml:17-20`, `.github/workflows/deploy-fleet.yml:38-40`). The run
   builds the verified commit and scales up the surge nodes. Then it bumps the tag, waits for the
   rollout, and scales the surge nodes back in (`.github/workflows/deploy-fleet.yml:174-472`). If
   another deploy-fleet run is in progress, the new run waits behind it. The group holds one running
   and one pending run, and a newer pending run replaces an older pending one
   (`.github/workflows/deploy-fleet.yml:13-14`, `.github/workflows/deploy-fleet.yml:22-24`).
8. Check that every deploy-fleet job passed. A green `settle-down` means both clusters run the new
   image and the surge nodes are gone.

> **`settle-down` fails:** the surge nodes stay up on purpose
> (`.github/workflows/deploy-fleet.yml:326-330`). Investigate before you scale them in.

## 5. Do not revert only the image tag

Reverting the deploy-fleet bump commit alone is not a safe rollback today, for two reasons.

The first reason is capacity. The backend DaemonSet declares no `updateStrategy`
(`deploy/base/spring-daemonset.yaml:9-13`). Its rollout removes the old pod before it starts the new
one on the same node (`.github/workflows/deploy-fleet.yml:170-172`). Each region runs one app node
outside a deploy, and two Spring heaps do not fit on one node
(`.github/workflows/deploy-fleet.yml:168-173`). Only deploy-fleet adds the surge node
(`.github/workflows/deploy-fleet.yml:174-272`). When ArgoCD syncs a tag revert, each region loses its
only backend pod until the old image starts.

The second reason is the pipeline. Merges land by rebase only
(`docs/adr/037-verification-lanes-and-continuous-deployment.md:11`), so the revert commit itself
lands on `main`. If that commit carries `[skip ci]`, PR Gate does not run on the pull request, and
its required checks block the merge (`.github/workflows/pr-gate.yml:5-7`). If it does not carry
`[skip ci]`, PR Gate runs on the push (`.github/workflows/pr-gate.yml:11-12`). deploy-fleet then
deploys the commit that run verified (`.github/workflows/deploy-fleet.yml:10-12`,
`.github/workflows/deploy-fleet.yml:17-20`). That commit still holds the bad code, so the bump goes
straight back to it.
