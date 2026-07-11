# Phase 11 Scenario Ledger: FE SSE invalidate→patch migration + errorCode degradation UX

Dossiers: /home/user/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-11/

Runner: Vitest (FE). Scoped command: `yarn --cwd frontend vitest run src/pages/planner src/shared/comment src/lib`
Tests live in `__tests__/` per FE convention. No Gradle / ArchUnit here.

## Binding decisions (recorded before burndown)
- D1 — FE consumes the Phase-10 `SseEnvelope` shape. New `SseEnvelopeSchema` in `frontend/src/shared/sse/schemas/` (exposed via `@/shared/sse`), `type: z.enum(['created','updated','deleted','notify:comment','notify:published','notify:recommended','settings:invalidated'])`; optional `entityType` (string), `userId` (number), `plannerId` (string), `entityId` (string), `deletedId` (string), `payload` (unknown, validated per-handler). Mirrors backend `SseEnvelope` (NON_NULL → all but `type` optional). No `.strict()` — forward-compat with added producer fields.
- D2 — Handler event-name KEYS unchanged (`SSE_EVENTS.PLANNER_UPDATE` etc.). No wire rename (out of scope, would break live SSE). Only handler BODIES change: parse envelope, `setQueryData` from `payload`/`deletedId`, NEVER `invalidateQueries` for the migrated caches, NEVER refetch (a refetch races replication).
- D3 — Planner id = `envelope.entityId`; deletes resolve `deletedId ?? entityId`. Created/updated `payload` validated against the existing server planner summary schema (`ServerPlannerSummarySchema`).
- D4 — `api.ts`: add `WriteTemporarilyUnavailableError` / `AuthTemporarilyUnavailableError` (mirror `ServiceUpdatingError`/`BackendUnavailableError`), thrown from the 503 branch keyed on the code. Write path: new `SaveErrorCode 'syncPaused'` → "saved locally, sync paused" (local save already succeeded). Auth path: honest toast.
- D5 — Reconnect jitter 0–5s added on top of the existing retry/backoff in the SSE reconnect path.
- D6 — Existing obsolete assertions in `useAppSse.test.tsx` (asserting `invalidate` for the migrated handlers) are revised by tdd-red as part of each scenario's RED step — this is a migration, the old characterization contradicts the new contract.

## Acceptance
- Test: frontend/src/pages/planner/hooks/__tests__/useAppSse.test.tsx::"'updated' planner envelope patches the detail cache from payload and does not invalidate" — opened RED (assertion: "expected {id:'p1',title:'Old Title'} to match object {title:'Changed Title'}" @ :257, 10 siblings green). closed GREEN at scenario 3 (14 passed useAppSse).

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | SseEnvelopeSchema validates full + minimal envelope (happy path) | closed | assertion: "expected false to be true" @ SseEnvelopeSchemas.test.ts:17 | green: 3 passed src/shared/sse/schemas |
| 2 | SseEnvelopeSchema rejects an unknown `type` | closed | (co-tested w/ #1; passed against stub, real schema green) | green: 3 passed src/shared/sse/schemas |
| 3 | planner 'created'/'updated' envelope → setQueryData patches list+detail from payload; zero invalidate (userPlanners dropped, not patched) | closed | assertion: "expected [...] to deeply equal ArrayContaining" + "invalidateQueries not called" | green: 14 passed useAppSse (acceptance closed here) |
| 4 | planner 'deleted' envelope → removes from list cache (deletedId??entityId) + purges local; zero invalidate | closed | assertion: "expected [...] to not deep equally contain ObjectContaining {id:'p1'}" @ :390 | green: 15 passed useAppSse |
| 5 | 'notify:published' envelope → patches planner list from payload (no list invalidate); still toasts | closed | assertion: "expected [{id:p2}] to deeply equal ArrayContaining [{id:p9}]" @ :431 | green: 16 passed useAppSse |
| 6 | notification envelope → patches notification cache | struck (notification inbox is a paginated NotificationInboxResponse keyed by (page,size); a single-notification payload prepend corrupts pagination metadata → not cleanly row-patchable. Also outside the planner/comment causal stack. handleNotification + handlePublished notification invalidations STAY as invalidations — honest + pagination-safe.) | | |
| 7 | account_suspended envelope → patches auth 'me' cache | struck (requirements.md L50/L88: invalidate→patch is BOUNDED to the causal stack — planner + comment recipient updates; migration surface lists only planner/comment hooks. Auth restriction is not in the causal stack, and the account_suspended payload is a partial suspension notice — the authoritative restriction state, with expiry, lives in /auth/me server-side — so invalidation/refetch is the honest choice.) | | |
| 8 | usePlannerCommentsSse patches comment TREE cache from payload comment DTO (root-append; reply-nesting deferred, no parentId) | closed | assertion: "expected [Array(1)] to deep equally contain ObjectContaining{id:c2}" | green: 24 passed src/shared/comment (tsc 0) |
| 9 | api.ts maps 503 WRITE_/AUTH_TEMPORARILY_UNAVAILABLE to typed errors | closed | assertion: "expected BackendUnavailableError to be an instance of WriteTemporarilyUnavailableError" @ api.test.ts:194 | green: 27 passed src/lib/api.test.ts |
| 10 | planner write hitting WriteTemporarilyUnavailableError → 'syncPaused' saved-locally path (no hard error) | closed | assertion: "expected 'saveFailed' to be 'syncPaused'" @ usePlannerSave.test.ts:269 | green: 19 passed usePlannerSave.test.ts |
| 11 | auth op hitting AuthTemporarilyUnavailableError → honest toast (centralized in handleBackendDownError) | closed | assertion: "expected vi.fn() to be called with ['errors.authUnavailable'], calls: 0" @ queryClient.test.ts:67 | green: 9 passed queryClient.test.ts (locale key added to static/i18n/EN/common.json — SUBMODULE) |
| 12 | SSE reconnect adds 0–5s jitter on top of existing retry backoff (both useSseEngine + usePlannerCommentsSse) | closed | assertion: "expected vi.fn() to be called 1 times, but got 2" @ useSseEngine.test.ts:185 | green: 14 passed src/shared/sse (acceptance 16 passed) |

## List Revisions
- [before scenario 5, refined at scenario 6] GOVERNING RULE for the migration: invalidate→patch happens where (a) the SSE envelope payload carries the affected cache's ROW AND (b) the cache is SHAPED to accept a row-level patch without corrupting derived metadata. PATCH: planner list/detail (payload=summary, plain array), comment list (payload=comment DTO, array), auth/me from account_suspended (payload=restriction/suspension, single object). STAY invalidate / dropped: userPlanners (dropped — reconciliation backstop), notification inbox+unread (paginated NotificationInboxResponse keyed by (page,size) — a single-notification prepend corrupts pagination totals; both handleNotification and handlePublished notification invalidations stay), handlePublished's notification invalidate (payload is a planner, not a notification DTO). Net: planner (3,4,5) + comment (8) patched; notifications + auth stay invalidated. GROUNDING: requirements.md L50 ("FE SSE handlers migrate from invalidate to patch (bounded: useAppSse.ts, comments SSE hook)") + L88 (migration surface = planner/comment hooks only) + L51 (necessity = RYW causal stack: planner reads + comment-create-discards-POST). Notifications/auth are outside the causal stack; their invalidations are NOT part of this migration.
- [before scenario 3] userPlanners cache: the local (IndexedDB) userPlanners cache is keyed `userPlannersQueryKeys.list(isAuthenticated)` and stores LOCAL planner shapes, not the server summary the SSE payload carries — cross-patching shapes is fragile. Decision: DROP the userPlanners `invalidateQueries` (no refetch, per contract) and lean on the existing `usePlannerSync` reconciliation backstop + the delete-path local purge. Scenario 3 patches list+detail from payload; userPlanners is only asserted NOT-invalidated. Recorded so tdd-green does not attempt a userPlanners setQueryData.

## Pipeline (post-burndown)
- burndown close (my independent run): `yarn --cwd frontend vitest run src/pages/planner src/shared/comment src/shared/sse src/lib` → Test Files 58 passed (58), Tests 735 passed | 1 skipped (736), EXIT=0. tsc -b EXIT=0.
- refactor: done (useAppSse.ts — extracted `upsertById` shared by handlePlannerUpdate+handlePublished; single-parse handlePublished). Suite green (599 passed sub-run + 16 net), test file byte-unchanged (md5 match), tsc 0. Benign divergence: malformed-JSON second-log level changed (untested path).
- verify: PASS after 2 rounds — verification.md Phase 11 (round 1 PARTIAL on mech §4 "7→patches"; reconciled mechanics.md §4 to record the bounded causal-stack carve-out per requirements L50/L88 + pagination/authoritative-state correctness; round 2 all 6 rows MET).
- capture: SKIPPED per user instruction
- staged: see Pipeline/staged below

## Strain reports (for refactor leg)
- useAppSse handlePlannerUpdate + handlePublished BOTH do the same "replace-or-insert into list cache by id" — DRY extraction candidate.
- handlePublished parses event.data TWICE (SseEnvelopeSchema.parse for payload + SsePublishedEventSchema.safeParse for toast) — each JSON.parse's the same string.
- `data.payload` is `z.unknown()` → handlers cast to `{ id?: string }`; optional-id truthiness guards throughout.
- usePlannerCommentsSse mixes local `SSE_CONFIG` (backoff) + shared `SSE_CONNECTION.MAX_JITTER` (jitter) — pre-existing duplicated-constant smell (local SSE_CONFIG mirrors SSE_CONNECTION); OUT OF SCOPE (larger change).
- comment-sse reply/parent nesting deferred (CommentNode has no parentId; producer unwired) — not a refactor item, a future scenario.
