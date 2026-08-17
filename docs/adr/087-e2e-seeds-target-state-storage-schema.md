# 087 e2e-seeds-target-state-storage-schema
epic: none · pr: none

## Decisions
- @e2e @storage @indexeddb — `e2e/src/localPlanner.ts` seeds IndexedDB rows in the storage
  schema the app currently reads (v2 flat `planner:{plannerId}` keys), takes
  `DB_NAME`/`DB_VERSION`/store name from `frontend/src/lib/storage.ts` exports rather than
  shadow constants, writes only when the key is absent, and closes its connection as soon
  as the puts are queued. IndexedDB's versioned open is a lock: an open at a higher version
  waits in `onblocked` for every lower-version connection to close, so the seeder's held
  version-1 connection deadlocked the app's version-2 upgrade and blanked every seeded
  page; a seeder pinned to its own copy of the version re-arms that trap at every schema
  bump, and an unconditional put re-run by the init script on each navigation clobbers
  whatever the app wrote since first load. REJECTED: seeding v1 rows and letting the app's
  `versionchange` migration rewrite them in every browser spec — the migration already has
  direct coverage in `frontend/src/lib/__tests__/storageMigration.test.ts`, and coupling
  every gesture spec to code scheduled for removal is the desync that produced the failure.
  REJECTED: keeping seeder-local constants bumped by hand — nothing enforces the bump.

## Takeaway
- takeaway: a test fixture that duplicates an application constant is a latent deadlock or
  desync armed by the next legitimate change to the original; import the identity, and make
  init-script writes idempotent because init scripts re-run on every navigation.
