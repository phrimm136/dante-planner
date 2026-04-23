---
name: commit-process
description: Full commit workflow — pre-commit validation, branch creation, commit message writing, staging, and checkout. Use this skill whenever the user asks to commit, create a commit, write a commit message, stage changes, finalize work, or prepare code for a PR. Also triggers on phrases like "commit the change", "ready to commit", "let's commit this", "wrap this up", or any git commit-related request.
---

# Commit Process

End-to-end workflow for turning completed work into a clean, well-documented commit on a feature branch.

## Step 1 — Validate Before Committing

Run all checks. A commit that breaks the build wastes everyone's time — catch it here.

| Check | Command |
|-------|---------|
| FE typecheck | `yarn --cwd frontend typecheck` |
| FE test | `yarn --cwd frontend test` |
| FE build | `yarn --cwd frontend build` |
| BE test | `./gradlew -p backend test` |
| BE build | `./gradlew -p backend build` |
| Mock deploy | `docker compose -f backend/docker-compose.local.yml up --build -d` |

Output redirection is enforced by the `check-output-redirect.sh` hook — it will block any test/build command that doesn't redirect to `/tmp/` with a date suffix. Follow the hook's required pattern.

**Mock deployment** spins up MySQL + Spring Boot via Docker Compose to validate Flyway migrations and context loading against a real database. After the backend health check passes (`curl -f http://localhost:8080/actuator/health`), tear down:
```
docker compose -f backend/docker-compose.local.yml down -v
```

If any check fails, diagnose and fix before proceeding. Do not skip checks — a "quick fix" that skips validation is how production breaks.

## Step 2 — Prepare the Branch

### Single-tree

Create a feature branch from `dev`:

```
git checkout -b <type>/<short-description>
```

| Prefix | Use when |
|--------|----------|
| `feat/` | Adding new functionality |
| `fix/` | Correcting a bug |
| `refactor/` | Restructuring without behavior change |
| `chore/` | Maintenance, dependencies, CI |
| `data/` | Static data updates (announcements, game data) |
| `docs/` | Documentation only |

Branch names use lowercase kebab-case, under 40 characters. The name should make the PR's purpose obvious at a glance.

**Examples:** `feat/add-identity-card`, `fix/refresh-token-rotation-race`, `data/v260317`

### Worktree — sync with dev before committing

The worktree's branch pre-exists from setup. Before committing, fast-forward it to `dev`'s current tip so your commits land linearly on top. While you were working, `dev` in the main worktree may have advanced; syncing now lets you resolve conflicts against the working tree (if any) rather than against pinned commits — and makes the later FF-merge in Step 5 trivial.

```bash
# Fetch latest dev from origin (in case the main worktree hasn't been updated)
git -C <worktree-path> fetch origin dev:dev

# Fast-forward the worktree branch to dev's tip
git -C <worktree-path> merge --ff-only dev
```

`--ff-only` is mandatory (per `feedback_no_merge_commits`). If FF fails because you've already made local commits that diverge, rebase instead: `git -C <worktree-path> rebase dev`.

After this step, your worktree branch == `dev`'s tip. New commits go on top linearly.

**Why `dev:dev` fetch syntax:** it fetches origin's `dev` and updates your local `dev` ref directly, even though `dev` is checked out in the main worktree. You're not touching the main worktree's checkout — just aligning the ref to origin.

**Why you can't `git checkout dev` inside the worktree:** a git worktree holds an exclusive checkout. `dev` is already checked out in the main worktree, so `git checkout dev` inside a feature worktree errors with "already checked out". Use `git -C <main-repo> ...` when you need to operate on dev directly.

## Step 3 — Write the Commit Message

This is the most important part. A good commit message is a gift to your future self and every reviewer who reads it.

### Format

```
type: imperative description

[optional body]
```

### Subject line

- **Imperative mood**: "add", "fix", "remove" — not "added", "fixes", "removed"
  - Litmus test: _"If applied, this commit will {your subject line}"_
- **50 characters max** — forces clarity; if you can't fit it, the commit might be doing too much
- **No period** at the end
- **Types**: `feat`, `fix`, `refactor`, `chore`, `data`, `docs`, `perf`, `test`, `style`

### When to write a body

Not every commit needs one. The decision rule:

**Subject-only** when the diff speaks for itself:
- `data: add 2026-03-17 announcement`
- `fix: typo in error message`
- `chore: bump vite to 6.2.1`

**Body required** when a reader would ask "why?" after seeing the diff:
- Bug fixes where the cause isn't obvious
- Refactors (why now? why this approach?)
- Feature additions (what motivated this?)
- Deletions (what makes removal safe?)
- Performance changes (what was measured?)

If you're unsure, ask: _"Would someone reading only the subject + diff understand both what changed and why?"_ If no, write a body.

### How to write the body

The body explains **why**, never what — the diff already shows what changed. Wrap at 72 characters. Separate from subject with a blank line.

Structure the body as: **problem → reasoning → approach**. Not all three are always needed — use judgment.

### Examples

**Bug fix — explain the cause, not the symptom:**
```
fix: add grace period to refresh token rotation blacklist

Without a grace period, concurrent requests using the same refresh
token race against the rotation. The second request finds its token
already blacklisted and forces a logout.

Allow a 30-second window where the old token remains valid after
rotation, matching the typical RTT for concurrent API calls.
```
The subject names the fix. The body explains the race condition (cause) and why 30 seconds (reasoning). A reader understands the tradeoff without opening the diff.

**Deletion — explain what guarantee makes removal safe:**
```
refactor: remove legacy session cleanup cron

Spring's ConcurrentMapCacheManager now handles eviction internally
since the upgrade to Boot 3.5. The manual cron was compensating for
a limitation that no longer exists.
```
Without this body, a reviewer might think the cron was still needed and revert.

**Feature — explain motivation, not implementation:**
```
feat: add permanent announcements with clickable links

Announcements previously expired after their date passed. Users
need persistent notices (Discord links, patch notes) that remain
visible regardless of date.

Permanent entries sort to the bottom of the list so time-sensitive
announcements still appear first.
```
The body captures a product decision (sort order) that isn't obvious from code alone.

**Non-obvious change — explain the "why" behind a surprising diff:**
```
fix: prevent racing of requests

Introduce a request id and a reference to latest request. Dismiss
incoming responses other than from latest request.

Remove timeouts which were used to mitigate the racing issue but are
obsolete now.

Refs: #123
```
The second paragraph explains why code was *removed* as part of a fix — counterintuitive without context.

### What to avoid

- Describing the diff ("changed X to Y in file Z") — the reader can see that
- `Co-Authored-By: Claude` or any AI signature — never add this
- Filler phrases ("This commit...", "This PR introduces...")
- Exclamation marks

## Step 4 — Stage and Commit

Stage only the files that belong to this logical change:
```
git add <specific-files>
```

Never `git add -A` or `git add .` — these risk pulling in unrelated work, secrets, or temp files.

### Include related docs

Before staging, check `git status` for untracked or modified files under `docs/` that were created as part of the same task — specs, plans, findings, research, reviews. These belong in the same commit as the code they document.

```
git status docs/
git add docs/<relevant-subdirectory>/
```

If a doc predates the current task and was only coincidentally modified, leave it out — same rule as code.

**One logical change per commit.** If your work spans multiple concerns (e.g., a bug fix + a refactor you noticed along the way), split them into separate commits on separate branches.

Use a HEREDOC so the message formats correctly:
```bash
git commit -m "$(cat <<'EOF'
type: description

Optional body here.
EOF
)"
```

## Step 5 — Wrap Up

### Single-tree

```
git checkout dev
```

The feature branch is ready for PR creation (separate workflow).

### Worktree — fast-forward dev from the main worktree

Because Step 2 (Worktree variant) aligned the worktree branch with `dev` before committing, your commits now sit linearly on top of `dev`'s tip. Move `dev` forward to include them:

```bash
git -C <main-repo-path> merge --ff-only <worktree-branch-name>
```

`--ff-only` enforces a no-merge-commit, linear history. If it errors, someone pushed to `dev` between your Step 2 sync and now — re-run the Step 2 worktree sync (fetch + ff-merge dev into worktree) and try again.

The worktree stays checked out on its branch; no need to return anywhere.
