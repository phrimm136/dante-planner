# 571 the-release-jar-is-built-by-a-job-without-credentials
epic: none · pr: none

## Decisions
- @ci @release @gradle — The release builds the boot jar on the runner, in a job of its own (`build-jar`) that holds `contents: read` and no secret, and `build-push` takes the jar as an artifact through `JAR_STAGE=jar-prebuilt`. Inside Docker the Gradle home was a BuildKit cache mount, which a fresh runner does not have (read from the Dockerfile, the download count was not measured), and releases met HTTP 429 from Maven Central. Re-check: `rg -n 'mount=type=cache,target=/root/.gradle' backend/Dockerfile`.
  REJECTED: running Gradle inside `build-push` — its plugins and build scripts are third-party code, and that job can mint an AWS identity.
  REJECTED: taking the jar of a test job — the released bytes would depend on a job whose purpose and inputs are the tests.
  INVARIANT: the only job of `deploy-fleet.yml` that runs Gradle has no environment, no secret and `contents: read` — `ReleaseJarTest`.
- @ci @release @cache — `build-jar` restores the Gradle cache that PR Gate's `test-backend` saves on a push to `main` and saves none. GitHub documents the default branch's cache as read-only for a `workflow_run` run, although a buildx cache export of such a run was observed to be written. Re-check: the "Restrictions for accessing a cache" section of GitHub's dependency caching reference.
  REJECTED: a cache of its own — by the documentation it could never be saved on a normal release.
  REJECTED: `cache-mode: write` on the job — third-party code would gain write access to the caches of `main`.
  INVARIANT: the restore key, its prefix and its paths equal those of `test-backend` — `ReleaseJarTest`.
  NON-GOAL: a release without any download — a dependency that only the production classpath resolves is fetched again, and a cache miss costs the whole download.
- @ci @release @wrapper — The Gradle wrapper carries `distributionSha256Sum`, because the release no longer takes Gradle from an image pinned by digest. The wrapper files and `backend/lombok.config` are deploy inputs, since a checkout build reads them.
  NON-GOAL: a check of a distribution that came from the cache — Gradle verifies only what it downloads.

## Takeaway
- takeaway: moving a build out of a container also moves its third-party code next to whatever the new place can reach.
