# Page slices

One vertical folder per route slice: route components at the slice root plus `components/`, `hooks/`, `lib/`, `schemas/`, `types/` (and `stores/` where needed). Schemas and types live per-slice — there is no `src/routes/`, `src/features/`, `src/schemas/`, or `src/types/`. Add `index.ts` (public API) only when the slice is imported from outside; the router deep-imports route components and needs no barrel.

## Static data hooks

- Paired singular `useSuspenseQuery` hooks per entity: `use<Entity>ListData` / `use<Entity>DetailData` in the slice's `hooks/`, built on the generic `useEntityListData` / `useEntityDetailData` from `@/shared/entityCatalog`.
- Build query options with `createStaticDataQueryOptions` (`src/lib/queryOptions.ts`) wrapping a literal dynamic `import('@static/data/...')` — never `fetch('/data/...')`.
- Query keys come from the factories in `src/lib/queryKeys.ts`; tuple shapes like `['identity', id]` and `['identity', id, 'i18n', language]` are load-bearing cache identities.
- Spec and i18n staleTime is `STATIC_DATA_STALE_TIME` (7 days, `src/lib/constants/api.ts`); server-backed queries pick a named window from `STALE_TIME`/`GC_TIME` in the same module.
- i18n hooks suspend. Read them under a per-component boundary shaped like the content it stands in for — precedent: `IdentityCard.tsx` wraps `<IdentityName>` in a `Suspense` whose `Skeleton` fallback is the two-line name it replaces.
- `useSearchTermSources` (`@/shared/filter`) is the one sanctioned non-suspending read, on plain `useQuery`: every item's search terms must exist before any card renders, so the grid can decide which cards are visible at all. Do not add a second such hook to dodge a boundary.

## Route components

- Validate route params before calling data hooks; wrap content in `ErrorBoundary` (`@/components/feedback/ErrorBoundary`) + `Suspense` — no early-return spinners.
- Two fallbacks, two scopes: `LoadingState` (`@/components/feedback/LoadingState`) is the route- and pane-shell fallback; a per-component boundary takes `Skeleton` from `@/components/ui/skeleton`, shaped like the content it replaces. `components/feedback/` has no barrel — import the deep path.
- Mutations report through the `MutationCache` sink in `src/lib/queryClient.ts`: a failure reaches `showError`, a success names `meta.successMessage` (with `meta.successParams`). Opt out with `meta.suppressErrorToast`, never with a bespoke toast.
- User-visible messages go through `showError` / `showAppError` / `showErrorMessage` / `showSuccess` / `showWarning` / `showInfo` from `@/lib/errorPresentation`, which take i18n keys, not strings. `sonner` belongs to that module alone — importing it from a slice is an error (`no-bare-toast` / `no-toast-import` in `frontend/scripts/ast-grep-rules/`).
- SSE invalidates and never patches: `useAppSse` (`pages/planner/hooks/`) calls only `invalidateQueries`, each `void`-ed for `no-floating-promises` — no `setQueryData`. Shared connection state lives in `shared/sse/stores/useSseStore`.

## Client state

- Zustand stores are per-slice (e.g. `pages/planner/stores/`); consume via atomic selectors (`useStore(s => s.field)`), never the bare store. Server data stays in TanStack Query, not Zustand.
- High-frequency (rAF-driven) UI state goes in a store written imperatively via `storeApi.getState()`, not `useState` — precedent: `deckVisibleCount` in `usePlannerEditorStore`.
