# 200 static-is-a-sibling-checkout-pinned-by-a-lock
epic: none · pr: none · supersedes: none

## Decisions
- @static @checkout — The game-data repository is one checkout beside the code repository,
  `~/github/LimbusPlanner-static`, and every checkout or worktree of the code reaches it through
  an untracked symlink at `<repo>/static`. The gitlink and `.gitmodules` are gone. With the
  submodule, every new worktree cloned the data afresh, the clone failed unless
  `protocol.file.allow=always` was passed, and a worktree whose gitlink moved was left on a stale
  checkout (`git -C <worktree> submodule status` showed `-`/`+` prefixes). Re-check:
  `git ls-files -s static | wc -l` prints 0 and `test -L static` succeeds in any checkout.
  REJECTED: keeping the submodule and sharing its object store (`--reference`) — still one
  checkout per worktree and still the file-protocol clone.
  REJECTED: an environment variable (`STATIC_DIR`) that every binding reads — `tsconfig`
  `paths` cannot read one, and Docker `COPY` does not follow a symlink out of the build
  context, so the path had to stay `<repo>/static` and only its nature change.
  REJECTED: vendoring the data into the code repository — the data repository is 1 GB-class and
  has its own pipeline and history (static/CLAUDE.md).
  INVARIANT: `static` is never a tracked path — `git ls-files -s static` prints nothing; the
  pre-commit hook refuses a commit whose staged paths include `static` or `static/…`.
  NON-GOAL: a release artifact for the data — the later asset-release unit replaces the lock's
  sha with a version and the checkout with a fetched directory at the same seam.
- @static @lock — The commit the code expects is one line in `static.lock` (a 40-hex sha of the
  data repository's `main`). A lock bump is a code commit, reviewed and gated like any other; CI
  checks that sha out as a real directory at `./static` with the existing deploy key. Re-check:
  `git -C ~/github/LimbusPlanner-static rev-parse HEAD` equals `cat static.lock` after
  `static sync`.
  REJECTED: tracking the data repository's `main` head in CI — a data push would change what a
  code commit builds against after the fact.
  INVARIANT: every PR Gate lane runs when `static.lock` changes — the `changes` job treats the
  lock as a backend, frontend and e2e path.
- @static @image — The backend image takes its data from a named build context `static`
  (`COPY --from=static data /app/data`); a local build passes the symlink's target, CI passes the
  checked-out directory. Re-check: `docker build` without `--build-context static=…` fails at
  `COPY --from=static` trying to pull `docker.io/library/static`, so the build cannot silently
  ship an empty data directory.
  REJECTED: copying `static/data` from the main context — Docker does not follow a symlink
  that leaves the context, so the local build would fail silently with an empty directory.
- @static @cli — The repository's repeated checks on the data checkout live in one CLI,
  `.claude/skills/static-checkout/bin/static`, with `doctor` (link target, checkout HEAD, lock
  sha, exit 1 on mismatch), `link`, `lock` (adopt the checkout's HEAD) and `sync` (check the
  lock out, refusing a dirty or ahead checkout and naming `static lock` as the next action).
  REJECTED: a paragraph in a runbook — the mismatch it describes is the one a human forgets to
  check.
- @static @pages — Cloudflare Pages fetches the data itself: its build command runs
  `frontend/scripts/fetch-static.sh`, which turns `static.lock` into a real directory at
  `<repo>/static` with a read-only token from the Pages environment, before the frontend build.
  Pages builds from its own git integration, so it has no data checkout, and without one the
  build stops at the lock check. Re-check: in a checkout without `static`, `static check` exits 2
  with `no data checkout at …/static`, and `vite build` fails with that line.
  REJECTED: a public data repository — the data repository stays private.
  REJECTED: moving the frontend deploy into the fleet deploy workflow now — that move belongs to
  the server-rendering change, and the script is the seam it reuses.
  INVARIANT: the token never appears in build output; the script never prints the URL that
  carries it — unenforced.

## Takeaway
- takeaway: keep the path, change what sits at it; pin by a value the review sees, not by a
  pointer the checkout hides.
