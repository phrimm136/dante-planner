# 086 legacy-metadata-key-tolerance
epic: none · pr: none

## Decisions
- @schema @storage @import — Legacy-key tolerance lives inside `PlannerMetadataSchema` itself: a preprocess step drops the keys on a named legacy list (`userId`) before the strict parse, so every consumer of the schema — storage load, list, export, import envelope — accepts records written before a key's removal without any consumer knowing the list exists. Metadata written by earlier app versions persists in IndexedDB rows and in export files on users' disks, and export files can never be rewritten, so read-side tolerance has no retirement condition. REJECTED: migrate-on-read calls at each consumer, in the keyword-migration style — the consumer list goes stale, and the omission that forced this decision was exactly the two consumers nobody remembered (export writer, import reader), which silently dropped one planner from an export and failed a whole import file on another.
- @schema @validation — The metadata gate stays strict; only keys on the legacy list are stripped, so an unknown key outside it still fails the parse. REJECTED: a non-strict object schema — it strips every unknown key silently, which blinds the schema-drift guard and converts future drift from a loud failure into quiet data loss.

## Takeaway
- takeaway: tolerance for known legacy data belongs in the shared schema every boundary already passes through, not in calls at the boundaries known today — and an explicit strip-list preserves the drift detection that blanket leniency destroys.
