# Post-Commit Hook Setup

## Problem

The git commit Bash tool post-use hook in `.claude/settings.json` was not working because:

**PostToolUse hooks do not support the Bash tool in Claude Code.**

Only Edit, MultiEdit, Write, and Read tools are supported by PostToolUse hooks.

## Solution

Use Git's native post-commit hook system instead of Claude Code's PostToolUse hooks.

## Setup

The hook is already configured in this repository. To set it up on a new machine or after a fresh clone:

```bash
# The hook file should be created at .git/hooks/post-commit
# This is done automatically by copying from the template below
```

## Git Hook Template

Create `.git/hooks/post-commit` with this content:

```bash
#!/bin/bash
# Git native post-commit hook
# Triggers the architecture map update after each commit

# Set CLAUDE_PROJECT_DIR for the hook script
export CLAUDE_PROJECT_DIR="$(git rev-parse --show-toplevel)"

# Call the existing Claude hook script
"$CLAUDE_PROJECT_DIR/.claude/hooks/post-commit-update-arch-map.sh"
```

Then make it executable:

```bash
chmod +x .git/hooks/post-commit
```

## Installation Script

For easy setup, run:

```bash
cat > .git/hooks/post-commit << 'EOF'
#!/bin/bash
# Git native post-commit hook
# Triggers the architecture map update after each commit

# Set CLAUDE_PROJECT_DIR for the hook script
export CLAUDE_PROJECT_DIR="$(git rev-parse --show-toplevel)"

# Call the existing Claude hook script
"$CLAUDE_PROJECT_DIR/.claude/hooks/post-commit-update-arch-map.sh"
EOF
chmod +x .git/hooks/post-commit
```

## Verification

Test the hook by making a commit:

```bash
git commit -m "test: verify post-commit hook"
```

You should see the architecture map update request output after the commit completes.

## How It Works

1. When you make a git commit, git automatically runs `.git/hooks/post-commit`
2. The hook sets `CLAUDE_PROJECT_DIR` environment variable (required by the script)
3. The hook calls `post-commit-update-arch-map.sh` which analyzes the commit
4. The script outputs a formatted request to update the architecture map
5. You manually update `docs/architecture-map.md` based on the request

## Important Notes

- **Git hooks are not committed to the repository** - Each developer must set up the hook locally
- The hook script (`post-commit-update-arch-map.sh`) is tracked in the repo
- The hook only outputs a reminder - you must manually update the architecture map
- The hook silently exits if `docs/architecture-map.md` doesn't exist

## Troubleshooting

If the hook doesn't run:

1. Check if the hook file exists: `ls -la .git/hooks/post-commit`
2. Check if it's executable: `chmod +x .git/hooks/post-commit`
3. Test it manually: `.git/hooks/post-commit`
4. Check for errors: `bash -x .git/hooks/post-commit`

If no output appears:

1. Verify `docs/architecture-map.md` exists
2. Check `CLAUDE_PROJECT_DIR` is set: `echo $CLAUDE_PROJECT_DIR` (in hook context)
3. Run the script directly: `CLAUDE_PROJECT_DIR=$(pwd) .claude/hooks/post-commit-update-arch-map.sh`
