---
status: Draft
tracking: none
---

# 0006 Asset extraction across worker processes

## Summary

Step 5 of the asset pipeline extracts one bundle at a time on one Python thread, and a full
decode of the game takes 825 s on a 12-core machine at 102 percent CPU. This proposal runs
the per-bundle work in a small pool of worker processes, keeps every decision that needs the
whole run (which output path a bundle wins, what the manifest records) in the parent, and
gates dispatch on bundle dependencies and a memory budget so the pool cannot run the box out
of memory the way the 2026-04 process-pool attempt did.

## Motivation

The pipeline's steady state holds (a no-change run of step 5 takes 6 s), but every full
decode, whether a layout bump or a client update that touches most bundles, costs 14 min,
and the requirement is a full pipeline run under 5 min. Every measured part of a bundle's
extraction is serial Python on the main thread (typetree reads, the plan of output paths,
pointer rewriting); the texture threads finish long before the main thread does and no
encoder setting moves the wall. The only lever left is running bundles concurrently in
separate interpreters.

## Current behavior

`extractAll._process_files` walks the bundle list in order: a two-thread prefetcher loads the
next bundles, the main thread extracts the current one, and a thread pool encodes textures.
Cross-run skipping reads the manifest (`_manifest.json`, 348 MB, 665 k files, 523 k object
paths) held in the main process. `objectDump` resolves a pointer into another bundle through
`ext.object_paths`, filled by earlier bundles of the same run and by the previous run's
manifest, which is why cache bundles walk in catalog dependency order. `PERFORMANCE.md`
records the 2026-04 process-pool attempt: each worker loaded the 3 GB typetree JSON and its
own bundle, and the machine went out of memory; typetrees have since moved to SQLite with
per-class lazy loads.

Measured on the 2026-09-17 client, one unit per process, output to a scratch tree:

| Unit | Wall | Peak RSS |
|---|---|---|
| `resources.assets` (117 MB, 9,916 outputs) | 44 s | 3.1 GB |
| `sharedassets2.assets` (27 MB, 2,985 outputs) | 27 s | under 3.1 GB |
| largest cache bundle (435 MB) | 2 s | 1.1 GB |
| second largest cache bundle (167 MB, 2,175 outputs) | 7 s | 1.4 GB |

The machine has 12 threads, 13 GB of memory with 6 GB available and 16 GB of swap.

## Prior art

Unity asset rippers that parallelize (AssetRipper, AssetStudio) do so per serialized file with
a shared read-only object index, which is the shape here. Python's `multiprocessing` with a
spawn context is the standard way to run CPU-bound work past the GIL; the repository already
runs the decompilation runtime dump through a fork pool sized by free memory and swap
(`runtime_common.fork_room`).

## Proposal

A unit is one game file or one cache bundle. The parent process keeps the manifest, decides
which units are unchanged (as today), and orders the rest: game files largest first, then cache
bundles in a topological order of the catalog's dependency map that visits larger bundles
first. A unit is ready when every dependency has finished. The parent dispatches the first
ready unit whose expected memory fits the in-flight budget, or the first ready unit when
nothing is in flight.

Each worker holds no manifest. A task carries the unit, the slice of the manifest the unit's
own label produced last run (path, hash, texture key), and every object-path entry finished
since the worker's last task. The worker extracts exactly as the serial code does, with three
substitutions: an output whose path and hash match the slice is held (not written), a texture
whose source key matches the slice and whose file exists is skipped before decoding, and
everything else is written under a per-unit staging directory. The worker returns the ordered
record of every output it produced or held, the object paths it added, its errors, tag counts,
unresolved pointer count, and the class set the bundle manifest entry needs.

The parent applies records in unit list order, never in completion order, through the same
claim rule the serial write used: a held record whose hash the manifest already carries is
marked seen; a staged file for a path nobody claimed this run is renamed into place; a path
already claimed this run is a dupe or a cross-phase collision, and its staged bytes are
discarded. Object-path deltas are merged the moment a result arrives, because each key has
exactly one writer, so a dependent unit can start before earlier unrelated units have been
applied.

Properties the design keeps from the serial run: the output tree and manifest are identical
for the same inputs, in-run path claims resolve in one fixed order, a pointer into a dependency
sees the dependency's current-run path, and an interrupted run keeps its progress through the
manifest checkpoint. Properties it changes: peak memory is the sum of the in-flight units'
peaks, bounded by the budget, and the dependency cache is per worker.

## Decisions

- **Workers hold no manifest.** Loading the 348 MB manifest per worker costs over 1 GB each;
  fork copy-on-write does not help because reference-count writes dirty the pages. The per-label
  slice is small and covers the case that matters (a bundle re-producing last run's paths); a
  path another bundle produced last run costs a staged write the parent discards.
- **Claims apply in list order.** Completion order varies run to run; applying claims in the
  static unit order keeps the tree deterministic. The cost is a reorder buffer of results, which
  is bounded because dispatch runs ahead in the same order.
- **Deltas flow on dispatch, not on a shared map.** A shared-memory map needs a serialization
  format and a lock; a per-worker cursor into the parent's delta list sends every entry to
  every worker once and needs neither.
- **The budget is expected bytes, not a worker count.** The unit peaks span 1.1 GB to 3.1 GB,
  so a fixed count either idles the pool on small bundles or overcommits on the two giant game
  files. Expected bytes come from the bundle's block table for bundles and from the measured
  ratio for raw serialized files.

## Decomposition

```
- claim-rule — `AssetExtractor.claim` decides skip, same, dupe, or write for one (path, hash); the serial write and the merge both call it
- unit-worker — a worker extracts one unit against a manifest slice and a staging directory and returns its record
- unit-scheduler — dependency-gated, budget-gated dispatch in list order with per-worker delta cursors (after: claim-rule, unit-worker)
- acceptance — a full decode under the new layout reports the same tree as the serial run and finishes under the target (after: unit-scheduler)
```

## Scenarios

```gherkin
Scenario: claim skips a path the manifest already carries
  Given the manifest holds "a.json" with hash "h1" and no unit claimed it this run
  When a unit claims "a.json" with hash "h1"
  Then the verdict is "skip" and "a.json" is seen

Scenario: claim marks a second claimant with other bytes as a dupe
  Given unit "cache/x" claimed "a.json" with hash "h1" this run
  When unit "game/y" claims "a.json" with hash "h2"
  Then the verdict is "dupe" and the collision list holds ("a.json", "cache/x", "game/y")

Scenario: a worker holds a slice match and stages everything else
  Given a slice {"a.json": ("h1", None)}
  When the worker writes "a.json" with bytes hashing to "h1" and "b.json" with bytes hashing to "h2"
  Then the record is [("a.json", "h1", None, False), ("b.json", "h2", None, True)] and only "b.json" exists under the staging directory

Scenario: a dependent unit waits for its dependency
  Given units A, B, C where C depends on A, and two idle workers
  When the scheduler dispatches
  Then A and B go out, C waits, and C goes out only after A's result arrives

Scenario: a unit larger than the budget runs alone
  Given a budget of 2 GB and a ready unit expected at 3 GB
  When nothing is in flight
  Then the unit is dispatched, and no other unit is dispatched until it returns

Scenario: results apply in list order
  Given units A then B, and B's result arrives first
  When the parent merges
  Then B's staged files stay staged until A's result arrives, and then both apply, A first

Scenario: a full decode reproduces the serial tree
  Given the serial run's output tree and manifest under layout "names-12"
  When the pool runs a full decode under layout "names-13"
  Then every file the manifest records has the same hash, and the unresolved pointer count is 53,498
```
