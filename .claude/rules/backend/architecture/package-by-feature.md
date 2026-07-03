# Backend Package-by-Feature Move Mechanic

How to relocate Java classes between packages at scale (layer→feature, or any large move) without content drift. Proven on the docs/32 consolidation (230 files → 8 feature packages, suite green).

## The unit that stays green

A package move breaks every importer of a moved class until they're fixed. The green-able atomic step is: **move one feature's files + fix every importer codebase-wide in one shot**, then compile. Never leave a feature half-moved — a half-moved package doesn't compile, and if uncommitted that endangers all prior work.

## The mechanic (per feature)

1. `mkdir -p <feature>/<layer>` dirs (git mv needs the destination).
2. `git mv` each file. Verify `git status` shows `R` (rename), not `D`+`??`.
3. `sed -i` the `package` declaration in each moved file.
4. Global `sed` across `src/main` + `src/test`: `import ...<oldpkg>.<Class>;` → `import ...<feature>.<layer>.<Class>;` for every moved class.
5. `compileJava compileTestJava` and fix the **three straggler classes** (below) until green.
6. Compile-green = checkpoint. Preserve internal layer subpackages (`<feature>/service`, `/controller`, `/repository`, …) so ArchUnit `..service..`/`..controller..` matchers keep working.

## The three stragglers (compile will surface these; grep won't)

- **(a) Same-package refs that had no import** — a moved class referenced a sibling left behind (add `import <oldpkg>.<Sibling>;` to the moved file), or a class that stayed referenced the moved one (add the new import to the stayer). This is the most common.
- **(b) Wildcard-import gaps** — a file with `import ...<oldpkg>.*;` relied on it to see the moved class; add a specific import.
- **(c) Inline fully-qualified names** — some code bodies use `org.danteplanner.backend.<oldpkg>.<Class>` inline (not an import). Sed the FQCN too.

## Gotchas

- Run seds inside `bash -c '...'` — the login shell is zsh and chokes on `${x%.*}` and assoc-arrays.
- `cp` may be aliased to `cp -i` (interactive) and silently not overwrite; revert via `sed -i '/pattern/d'` or `git checkout HEAD -- <single-file>`, never rely on `cp`.
- Restrict seds to `*.java` — a broad `grep -rl` matches `.sql`/`.properties` in `src/main/resources` and will corrupt them.
- **Never `git checkout .`** with uncommitted work in the tree — it destroys everything. Revert one HEAD-unchanged file via pathspec `git checkout HEAD -- <file>` only.
- Check for same-simple-name collisions first (`find -name '*.java' | sed 's|.*/||' | sort | uniq -d`) — none means no import can resolve to the wrong class.

## Delegating to an agent is allowed for this mechanic

The "no code-writer agents for pure moves" rule (page-slice-migration.md) targets **Read→Write body drift** — agents that read a file and rewrite it reformat/alter bodies. This sed/git-mv mechanic never Read→Writes bodies (only package/import lines change), so the drift failure mode is absent and the prohibition doesn't bind. Give the agent the mechanic + a complete Class→feature mapping + a per-feature compile checkpoint. Verify the result yourself: old layer packages empty, full suite green, and an FQCN grep over resources.

## Enforce boundaries WITH the move, and bite-test them

Ship an ArchUnit `FeatureBoundaryTest` in the same change (per `invariant-the-backend-s-strict-layering-controller`): a shared-is-sink rule + a per-feature cross-edge allowlist that freezes the current graph. **Bite-test it** — inject a real illegal dependency (a field of a forbidden feature's type), confirm the build fails, revert. A boundary rule that passes but was never proven to fail is a comment.

## `shared/` is rarely a pure sink

Cross-cutting infrastructure that references feature code (`GlobalExceptionHandler` importing every feature's exceptions, security filters importing user services) cannot live in a pure-sink `shared/`. Either relocate those classes (a logic change — separate task) or encode the sink rule in its truthful residual form (`shared !→ {the features it genuinely doesn't touch}`) and record the relocation as a follow-up. Do not fake a pure-sink rule that fails or forces out-of-scope moves.
