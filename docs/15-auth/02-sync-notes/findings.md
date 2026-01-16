# Learning Reflection: Privacy-Respecting Sync Architecture

## What Was Easy

- Adapter pattern split - clear separation between IndexedDB (SaveAdapter) and API (SyncAdapter)
- Entity modeling - UserSettings @MapsId pattern straightforward once research clarified
- Conflict detection - syncVersion as server-controlled counter eliminated ambiguity
- Settings filtering - ConcurrentHashMap cache per SSE connection avoided per-event DB queries
- Zod schema reuse - existing patterns provided proven validation structure

## What Was Challenging

- SSE async dispatch filter chain - Spring Security re-runs filters on ASYNC dispatch, JWT fails silently with 403
- Autosave race conditions - debounce closure captured stale state, required timer clearing AND component remount
- Storage key migration - drafts vs saved prefixes created duplicates, needed unified key scheme
- Sync pagination - listAll() only fetched page 0, users with 100+ planners missed server updates
- "Keep Both" scope ambiguity - needed clarification on fork model (copy + redirect + new UUID)

## Key Learnings

- Client ID propagation prevents duplicate entries - server accepting client UUID solves race conditions
- Settings cache invalidation critical - stale cache filters events wrong after toggle
- beforeunload needs multiple refs - local changes, server sync, intentional navigation tracked separately
- Batch operations expose pagination gaps - single-planner testing masks limits
- ThreadLocal cleanup requires finally block - exceptions mid-validation cause memory leaks
- Component remount (key prop) legitimate for state reset after authoritative changes
- SSE rate limiting needs reconnection headroom - page refresh triggers multiple quick connects

## Spec-Driven Process Feedback

- Research.md highly accurate - complexity came from unspecified edge cases, not misread requirements
- Sequential phase order worked - bottom-up prevented integration loops
- Edge cases E3/E4 remain undefined - spec should force decision on timeout/quota error handling
- "Keep Both" fork model needed concrete example in research

## Pattern Recommendations

- Split adapters pattern (SaveAdapter + SyncAdapter) → add to fe-data skill
- syncVersion counter for conflict detection → add to be-service pattern
- Settings cache per SSE connection → add to be-async pattern
- Component remount via key prop for state reset → add to fe-component with caveat
- ThreadLocal cleanup in finally blocks → enforce in be-service validation pattern
- ASYNC dispatch security config → add to be-security pattern

## Next Time

- Stress-test pagination early with 200+ items before declaring sync complete
- Simulate network latency in tests to catch race conditions
- Request edge case decisions before implementation, not after
- Capture lessons per 5-issue batch, not just at end
- Document async dispatch gotchas in security patterns
