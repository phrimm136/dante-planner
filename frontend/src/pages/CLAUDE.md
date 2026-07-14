# Page slices

One vertical folder per route slice: route components at the slice root plus `components/`, `hooks/`, `lib/`, `schemas/`, `types/` (and `stores/` where needed). Schemas and types live per-slice — there is no `src/routes/`, `src/features/`, `src/schemas/`, or `src/types/`. Add `index.ts` (public API) only when the slice is imported from outside; the router deep-imports route components and needs no barrel.

## Static data hooks

- Paired singular `useSuspenseQuery` hooks per entity: `use<Entity>ListData` / `use<Entity>DetailData` in the slice's `hooks/`.
- Build query options with `createStaticDataQueryOptions` (`src/lib/queryOptions.ts`) wrapping a literal dynamic `import('@static/data/...')` — never `fetch('/data/...')`.
- Query keys come from the factories in `src/lib/queryKeys.ts`; tuple shapes like `['identity', id]` and `['identity', id, 'i18n', language]` are load-bearing cache identities.
- Spec and i18n staleTime is `STATIC_DATA_STALE_TIME` (7 days, `src/lib/constants.ts`); i18n queries set `keepPrevious` to avoid the language-switch flash.

## Route components

- Validate route params before calling data hooks; wrap content in `ErrorBoundary` (react-error-boundary) + `Suspense` with `LoadingState`/`ErrorState` from `@/components/feedback` — no early-return spinners.
- Mutations: `useMutation` + `invalidateQueries` + sonner toasts.
- SSE follows invalidate-then-patch: `useAppSse` (`pages/planner/hooks/`) applies server events with `setQueryData`/`invalidateQueries`; shared connection state lives in `shared/sse/stores/useSseStore`.

## Client state

- Zustand stores are per-slice (e.g. `pages/planner/stores/`); consume via atomic selectors (`useStore(s => s.field)`), never the bare store. Server data stays in TanStack Query, not Zustand.
- High-frequency (rAF-driven) UI state goes in a store written imperatively via `storeApi.getState()`, not `useState` — precedent: `deckVisibleCount` in `usePlannerEditorStore`.
