# Learning Reflection: Local-First Auto-Save

## What Was Easy

- Dual storage adapter abstraction already existed, making routing split straightforward (auto-save to IndexedDB, manual save to server)
- Existing state comparison utility (stateToComparableString) prevented redundant saves without additional logic
- beforeunload API integration was simple with standard useEffect cleanup pattern
- date-fns library provided formatDistanceToNow with no configuration needed
- Comprehensive spec eliminated implementation ambiguities, reducing design decisions

## What Was Challenging

- Race condition between auto-save and manual save required guard clause (isSaving flag)
- Edit mode initialization showed false "unsynced" state on first render, needed syncVersion >= 1 check
- Date validation failures when formatDistanceToNow received invalid ISO strings, solved with try-catch and isNaN checks
- useCallback dependency chain had stale closures when isSaving dependency was missing
- Test mocking with top-level vi.mock and async issues required switching to factory function pattern

## Key Learnings

- Bypassing adapter layer for auto-save performance gain requires maintaining manual save adapter routing to preserve architectural abstractions
- Browser quota management is natural limiter when artificial draft limits removed—no app-level enforcement needed
- State comparison prevents infinite loops in debounced saves by detecting unchanged state before writing
- beforeunload warning works only on browser native events, not React navigation—separate cleanup patterns needed per tab
- Relative time formatting must update on every render cycle to maintain live accuracy as time passes
- IndexedDB accepts incomplete planners while server validation preserves data integrity—separation of work-in-progress from published state
- Cross-device sync shifts from near real-time to eventual consistency on manual save—acceptable trade-off for 99% server load reduction

## Spec-Driven Process Feedback

- research.md mapping was precise—identified all file locations and line numbers, zero discovery gaps
- plan.md execution order worked well with clear phase dependencies and verification checkpoints after each step
- Spec assumed isSaving flag behavior without explicit mention; discovered through code review that it already existed
- Testing guidelines in instructions.md (63 manual steps + 57 assertion checkboxes) exceeded typical need but caught edge cases
- Risk assessment section proved valuable—identified race condition and validation scenarios before implementation

## Pattern Recommendations

- Document dual-storage routing split pattern: auto-save bypass adapter for performance, manual save preserves adapter for auth routing
- Warn against useCallback dependencies on derived state—favor tracking explicit conditions (isSaving, isAuthenticated)
- Add beforeunload cleanup pattern to fe-component skill with note that each browser tab needs independent tracking
- Document lastSyncedStateRef pattern for detecting unsynced changes when state mutations are complex
- Add relative time formatting pattern showing need for re-render on time progression, not state change

## Next Time

- Include implementation checklist in research.md (types, interfaces, imports needed) to catch missing dependencies earlier
- Separate manual verification steps by phase in instructions.md with shorter per-phase checklists instead of one massive 63-step list
- Document state comparison utility behavior explicitly—prevent assumption that debounce alone prevents infinite loops
- Test mock patterns in frontend specs to identify vi.mock async issues before implementation
- Validate date input format assumptions in timestamp features early via integration tests
