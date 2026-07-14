# Frontend

React 19 + TypeScript + Vite + TanStack Query/Router + Zustand + Zod + Tailwind + shadcn/ui. Package manager: Yarn.

## Commands & deploy

- Lint is oxlint: `yarn lint` (type-aware, warnings denied, config `.oxlintrc.json`). There is no eslint. Format: `yarn fmt` (oxfmt). Typecheck: `yarn typecheck` (`tsc -b`).
- From the repo root, always target the frontend: `yarn --cwd frontend vitest run`, `yarn --cwd frontend tsc -b`. Bare `vitest`/`tsc` at the repo root creates a stray `node_modules/.vite` there and is blocked by a hook.
- Deploy is Cloudflare Pages serving plain `vite build` output — no Workers, no wrangler config.

## Layer boundaries (oxlint-enforced)

Four layers, each module in exactly one: `pages/` (route slices), `shared/` (co-owned domain modules), `components/` (domain-free React: `ui`/`layout`/`feedback`/`hooks` + vendored `tiptap-*`), `lib/` (domain-free non-React).

- Deep imports matching `@/pages/*/**` are forbidden (`no-restricted-imports` in `.oxlintrc.json`); reach a slice or shared module only through its public API (`@/pages/<slice>`, `@/shared/<concept>`). `src/lib/router.tsx` is the sole exemption.
- Intra-slice imports must be relative (`./components/X`), not `@/pages/...`.
- `components/` and `lib/` are dependency sinks — they must not import page slices.
- A module imported by 2+ slices/domains lives in shared space, never inside a consuming slice.

## Routing

Code-based routes only, defined in `src/lib/router.tsx` with `lazyRouteComponent` — no file-based routing, no generated route tree, no `_layout`/`__root` file conventions. Internal navigation uses `<Link>`/`useNavigate`, never `<a href>`.

## React Compiler

`babel-plugin-react-compiler` is enabled in `vite.config.ts`. Do not hand-write `memo`/`useCallback`/`useMemo` for what it covers; manual memoization requires profiling evidence.

## Constants & styling

- Game vocabulary (sinners, affinities, level caps) → `src/shared/gameData/constants.ts`; app config (delays, stale times, colors) → `src/lib/constants.ts`. No hardcoded hex colors or magic numbers in components — add to the right module first.
- Color/style exports are `UI_COLORS`, `SANITY_INDICATOR_COLORS`, `PASSIVE_INDICATOR_COLORS`, and `SECTION_STYLES` — there is no `COLORS` export.
- Class merging via `cn` from `src/lib/utils.ts`. `lg:` (1024px) is the mobile/desktop breakpoint.
- Asset paths go through the helpers in `@/shared/assets`, never hardcoded `"/images/..."` strings.

## Forms & debounce

- Forms are hand-rolled controlled components (`useState` + submit handler + mutation). react-hook-form and @hookform/resolvers are NOT installed — do not introduce them.
- No debounce library — debouncing is hand-rolled `setTimeout` + cleanup using `SEARCH_DEBOUNCE_DELAY` / `AUTO_SAVE_DEBOUNCE_MS` from `src/lib/constants.ts`. Don't add a library without a decision.

## Data & i18n

- External data (static JSON, API responses) is validated with Zod at the boundary via `validateData` (`src/lib/validation.ts`); schemas use `.strict()`, types come from `z.infer` — never a parallel interface.
- All user-visible text goes through `t()` (react-i18next).

## Testing

- MSW handlers live in `src/test-utils/handlers.ts`; `vitest.setup.ts` stubs `globalThis.fetch = vi.fn()` suite-wide, so network-level test files scope their own MSW lifecycle and restore the fetch stub — see `src/test-utils/mswServer.ts`.
- Mock at the boundary the test crosses: HTTP → MSW per-test handlers; pure store state → no mocks; injected adapter → `vi.mock` the adapter module, not fetch.
