# 505 dependency-updates-merge-themselves-after-the-cooldown-and-the-gate
epic: none · pr: none

## Decisions
- @dependencies @dependabot @auto-merge — A Dependabot pull request whose updates are all minor or patch merges itself by rebase once the required PR Gate checks pass; a pull request holding any major waits for a person. The defense is the seven-day cooldown on every ecosystem, which holds a new release back until a malicious one is usually found and yanked, plus the required checks, which catch what an update breaks; a person reading a minor bump adds neither.
  REJECTED: auto-merging every update — a major changes APIs and needs a person to adapt the code, so the gate fails or, worse, passes on a behavior change no test covers.
  REJECTED: merging every update by hand — the queue grows faster than it is read and stale pull requests pile up until they are closed and regenerated in bulk.
  INVARIANT: auto-merge is enabled only when the highest update type in the pull request is not a major — the workflow reads fetch-metadata's `update-type`, which is the highest level across every update in a group, so a security group carrying one major is excluded.
  INVARIANT: auto-merge is enabled only on a pull request Dependabot opened and only on a run Dependabot triggered, and a push by anyone else withdraws it — the job's author and actor conditions; a person who pushes to a Dependabot pull request merges it by hand.
  NON-GOAL: changing the package manager — pnpm is on hold; the frontend stays on its current manager.
- @dependencies @auto-merge @deploy — A merge the workflow's auto-merge performs is expected not to start the `push` workflows on `main`, so PR Gate on `main` and the deploy run with the next merge a person makes. GitHub documents that an event triggered by the repository's `GITHUB_TOKEN` creates no new workflow run, except `workflow_dispatch`, `repository_dispatch` and some `pull_request` events (the `GITHUB_TOKEN` concepts page, "When GITHUB_TOKEN triggers workflow runs"); whether a merge GitHub performs for auto-merge enabled by that token counts as such an event is unverified.
  NON-GOAL: deploying each Dependabot merge on its own — a Dependabot change reaches production with the next merge to `main` that a person makes.
- @dependencies @docker @digest — The backend's base images are pinned as the narrowest tag that names the current build plus the image index digest, the multi-platform list, so the builder resolves on the runner's platform and the runtime on `linux/arm64`. A floating tag changes under a fixed Dockerfile, so the same commit could build different bytes; Dependabot updates the tag and the digest together.
  REJECTED: floating tags — a rebuild silently takes whatever was pushed to the tag that day, with no pull request and no cooldown.
  INVARIANT: no Dependabot pull request proposes a JDK major — Dependabot ignores `eclipse-temurin` and `gradle` semver-majors, and the gradle tag's JDK sits in its suffix, which Dependabot never changes; JDK moves are made by hand.
- @dependencies @gradle @graph — The backend submits its resolved Gradle dependency graph on every push to `main`, so alerts name the versions the build resolves, not what a static read of the build file guesses.
  REJECTED: Gradle dependency verification metadata — Dependabot does not update it, so every backend update needs a hand edit, and Maven Central releases are immutable, so the risk it would catch is a new malicious release, which the cooldown already holds back.
  REJECTED: static manifest parsing alone — it reported Tomcat 10.1 and Spring 6 alerts for a build that resolves neither.

## Takeaway
- takeaway: an update needs a person only where a person adds a check the machine lacks; for the rest, a waiting period and the test gate are the review.
