# Research: Integrate TanStack Query and Router

## Overview of Codebase

### Current Frontend Setup

The LimbusPlanner frontend is currently a minimal Vite + React 19 + TypeScript application with Tailwind CSS and shadcn/ui configured. The key characteristics are:

**Current Technologies:**
- **React 19.2.0** - Latest version using `createRoot` API
- **Vite 7.2.2** - Modern build tool with fast HMR
- **TypeScript 5.9.3** - Type safety with strict mode
- **Tailwind CSS v4** - Utility-first styling
- **shadcn/ui** - Component library

**Current Structure:**
```
frontend/src/
├── main.tsx          # Entry point (uses createRoot)
├── App.tsx           # Root component (single page, no routing)
├── index.css         # Global styles with Tailwind
├── components/
│   └── ui/          # shadcn components (Button)
├── lib/
│   └── utils.ts     # cn utility function
└── assets/          # Static assets
```

**Key Observations:**
- No routing system currently in place
- No data fetching library configured
- Single-page application with one component
- Uses React 19's `createRoot` API (compatible with TanStack)
- TypeScript 5.9.3 meets TanStack Router requirements (v5.3+)
- `@types/node` already installed (needed for path resolution)

### TanStack Query v5 (React Query)

TanStack Query (formerly React Query) is a powerful data synchronization library for React applications.

**Key Features:**
- Automatic caching with smart refetch strategies
- Background data updates
- Optimistic updates and mutations
- Pagination and infinite scroll support
- Request deduplication
- Built-in dev tools for debugging

**React 19 Compatibility:**
- ✅ Official documentation states "React v18+" compatibility
- ✅ Uses standard React hooks (works with React 19)
- ✅ Compatible with `createRoot` API
- ✅ No reported issues with React 19.2.0

**Latest Version (2025):**
- Current version: v5.90.7+
- Stable and actively maintained
- TypeScript-first design

**Installation Pattern:**
```bash
yarn add @tanstack/react-query
yarn add -D @tanstack/react-query-devtools  # Optional but recommended
```

**Basic Setup Pattern:**
```typescript
// src/lib/queryClient.ts
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,        // 1 minute
      gcTime: 5 * 60 * 1000,       // 5 minutes (formerly cacheTime)
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
```

```typescript
// src/main.tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './lib/queryClient'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
```

### TanStack Router v1

TanStack Router is a modern, type-safe routing solution built specifically for React applications.

**Key Features:**
- Type-safe routing with full TypeScript inference
- File-based or code-based route configuration
- Built-in data loading (loaders)
- Search param validation
- Nested layouts and routes
- Code-splitting support
- Route-based data prefetching
- Built-in dev tools

**React 19 Compatibility:**
- ✅ Requires React v18+ with `createRoot` support
- ✅ React 19.2.0 is fully compatible
- ✅ Works with React-DOM v18+
- ✅ No migration issues from React 18 → 19

**TypeScript Requirements:**
- Minimum: TypeScript v5.3.x
- Current project: TypeScript 5.9.3 ✅
- Recommendation: Keep updated with latest TS versions

**Latest Version (2025):**
- Current version: v1.99+ (approaching v2)
- Stable and production-ready
- Active development and community

**Two Routing Approaches:**

1. **File-Based Routing (Recommended)**
   - Routes are automatically generated from file structure
   - Convention: `src/routes/` directory
   - Files like `index.tsx`, `about.tsx` become routes
   - Nested routes via folder structure
   - More intuitive for large applications

2. **Code-Based Routing**
   - Manually define routes in code
   - More explicit control
   - Better for migrating from React Router
   - Good for smaller applications

**Installation Pattern:**
```bash
yarn add @tanstack/react-router
yarn add -D @tanstack/router-plugin  # Vite plugin (REQUIRED)
yarn add -D @tanstack/router-devtools  # Optional dev tools
```

**Vite Plugin Setup (CRITICAL):**
```typescript
// vite.config.ts
import { TanStackRouterVite } from '@tanstack/router-plugin/vite'

export default defineConfig({
  plugins: [
    TanStackRouterVite(),  // MUST come BEFORE react() plugin
    react(),
    tailwindcss(),
  ],
})
```

**Why the plugin order matters:**
- TanStack Router plugin generates route tree code
- React plugin needs to process that generated code
- Wrong order = TypeScript errors or missing route types

**Basic Router Setup Pattern:**
```typescript
// src/lib/router.tsx
import { createRouter, createRootRoute } from '@tanstack/react-router'

const rootRoute = createRootRoute({
  component: () => <Outlet />,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: HomePage,
})

const routeTree = rootRoute.addChildren([indexRoute])

export const router = createRouter({ routeTree })
```

```typescript
// src/main.tsx
import { RouterProvider } from '@tanstack/react-router'
import { router } from './lib/router'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
```

### Integration Pattern: Query + Router

TanStack Query and Router are designed to work together seamlessly:

**Route Loaders with React Query:**
```typescript
const postRoute = createRoute({
  path: '/posts/$postId',
  loader: ({ params }) => ({
    queryClient: () => queryClient,
    queryKey: ['posts', params.postId],
    queryFn: () => fetchPost(params.postId),
  }),
})
```

**Benefits of Integration:**
- Route loaders can prefetch data
- Navigate between pages without loading spinners
- Share query cache across routes
- Type-safe params and data

## Codebase Structure

### Current Structure
```
frontend/
├── vite.config.ts          # Vite configuration
├── package.json            # Dependencies
├── tsconfig.json           # Root TS config with path aliases
├── tsconfig.app.json       # App TS config with path aliases
├── components.json         # shadcn config
├── src/
│   ├── main.tsx           # Entry point ← NEEDS MODIFICATION
│   ├── App.tsx            # Current root ← WILL BECOME ROUTE
│   ├── index.css          # Global styles (Tailwind)
│   ├── lib/
│   │   └── utils.ts       # Utilities
│   └── components/
│       └── ui/            # shadcn components
```

### Target Structure (After Integration)
```
frontend/
├── vite.config.ts          # ← ADD TanStack Router plugin
├── package.json            # ← ADD TanStack dependencies
├── src/
│   ├── main.tsx           # ← WRAP with QueryClientProvider + RouterProvider
│   ├── lib/
│   │   ├── utils.ts       # Existing utilities
│   │   ├── queryClient.ts # 🆕 Query client configuration
│   │   └── router.tsx     # 🆕 Router setup
│   ├── routes/            # 🆕 Route definitions
│   │   ├── __root.tsx    # 🆕 Root layout route
│   │   ├── index.tsx     # 🆕 Home page (current App.tsx content)
│   │   └── about.tsx     # 🆕 Example route for testing
│   ├── components/
│   │   ├── ui/           # shadcn components
│   │   └── layout/       # 🆕 Layout components (Header, Footer)
│   └── hooks/            # 🆕 Custom React Query hooks
│       └── useExampleQuery.ts  # 🆕 Example query hook
└── routeTree.gen.ts       # 🆕 Auto-generated by router plugin
```

### File Responsibilities

**src/lib/queryClient.ts**
- Create and configure QueryClient
- Set default options (staleTime, retry, etc.)
- Export for use in main.tsx and route loaders

**src/lib/router.tsx**
- Import all routes
- Create router instance
- Configure router options
- Export for RouterProvider

**src/routes/__root.tsx**
- Root layout route
- Contains common UI (header, footer, sidebar)
- Renders `<Outlet />` for nested routes
- Can include QueryClientProvider scope if needed

**src/routes/index.tsx**
- Home page route (path: '/')
- Can move current App.tsx content here
- First route users see

**src/main.tsx (Modified)**
- Import queryClient and router
- Wrap with QueryClientProvider
- Provide router to RouterProvider
- Include dev tools in development

### Path Aliases Already Configured
The project already has path aliases set up:
- `@/` → `./src/`
- Works in: tsconfig.json, tsconfig.app.json, vite.config.ts

**Usage:**
```typescript
import { Button } from '@/components/ui/button'
import { queryClient } from '@/lib/queryClient'
import { router } from '@/lib/router'
```

## Gotchas and Pitfalls

### 1. Vite Plugin Order is Critical ⚠️

**Issue:** TanStack Router plugin MUST come before React plugin.

**Wrong order (will fail):**
```typescript
export default defineConfig({
  plugins: [
    react(),               // ❌ React first
    TanStackRouterVite(),  // ❌ Router second = ERRORS
    tailwindcss(),
  ],
})
```

**Correct order:**
```typescript
export default defineConfig({
  plugins: [
    TanStackRouterVite(),  // ✅ Router first
    react(),               // ✅ React second
    tailwindcss(),
  ],
})
```

**Why:** The router plugin generates TypeScript route definitions. React plugin needs to process those generated files. Wrong order causes TypeScript errors and missing types.

**Error if wrong:** `Cannot find module 'routeTree.gen'` or route types are undefined.

### 2. React Query v5 Renamed Properties ⚠️

**Issue:** React Query v5 renamed some properties from v4.

**Breaking changes:**
```typescript
// v4 (OLD)
useQuery({
  cacheTime: 5 * 60 * 1000,  // ❌ No longer exists
})

// v5 (NEW)
useQuery({
  gcTime: 5 * 60 * 1000,     // ✅ Renamed to gcTime (garbage collection time)
})
```

**Other changes:**
- `keepPreviousData` → `placeholderData: keepPreviousData`
- `isLoading` → `isPending` (for initial load)
- Query keys are now type-safe arrays

**Mitigation:** Always use v5 documentation, not old blog posts.

### 3. QueryClient Must Be Stable Reference ⚠️

**Issue:** Creating QueryClient inside component causes infinite re-renders.

**Wrong:**
```typescript
function App() {
  const queryClient = new QueryClient()  // ❌ New instance every render!
  return <QueryClientProvider client={queryClient}>...</>
}
```

**Correct:**
```typescript
// In separate file: src/lib/queryClient.ts
export const queryClient = new QueryClient()

// In main.tsx
import { queryClient } from '@/lib/queryClient'
<QueryClientProvider client={queryClient}>...</>
```

**Why:** QueryClient holds cache. Creating new instance loses all cached data and causes re-renders.

### 4. Router Must Be Created Outside React ⚠️

**Issue:** Router should be created at module level, not inside components.

**Wrong:**
```typescript
function App() {
  const router = createRouter(...)  // ❌ Creates new router every render
}
```

**Correct:**
```typescript
// In src/lib/router.tsx
export const router = createRouter(...)

// In main.tsx
import { router } from '@/lib/router'
<RouterProvider router={router} />
```

**Why:** Similar to QueryClient - router holds state, must be singleton.

### 5. Route File Naming Conventions (File-Based Routing) ⚠️

**Issue:** TanStack Router has specific file naming rules.

**Special files:**
- `__root.tsx` - Root layout route (double underscore)
- `index.tsx` - Index route (matches parent path)
- `$paramName.tsx` - Dynamic route parameter
- `_layout.tsx` - Layout route (underscore prefix, pathless)

**Examples:**
```
routes/
├── __root.tsx       → Path: / (root layout)
├── index.tsx        → Path: / (home page)
├── about.tsx        → Path: /about
├── posts/
│   ├── index.tsx    → Path: /posts
│   └── $postId.tsx  → Path: /posts/:postId (dynamic)
```

**Common mistakes:**
- Forgetting double underscore in `__root.tsx`
- Using `root.tsx` instead (won't be recognized)
- Not understanding `index.tsx` = parent path

### 6. TypeScript Generated Files ⚠️

**Issue:** Router plugin generates `routeTree.gen.ts` file.

**Important notes:**
- ⚠️ **DO NOT** edit this file manually
- ⚠️ **DO NOT** commit to `.gitignore` (needed for types)
- ✅ **DO** commit to version control
- ✅ **DO** regenerate if routes change

**When it regenerates:**
- Automatically when dev server runs
- When route files are added/modified/deleted
- May need manual restart if not updating

**If types are missing:**
```bash
# Restart Vite dev server
yarn dev
```

### 7. Search Params Validation ⚠️

**Issue:** TanStack Router can validate search params, but must be set up.

**Without validation:**
```typescript
const searchParams = useSearch()  // Type: unknown, unsafe
```

**With validation (using Zod):**
```typescript
import { z } from 'zod'

const searchSchema = z.object({
  page: z.number().default(1),
  query: z.string().optional(),
})

const route = createRoute({
  path: '/search',
  validateSearch: searchSchema,
})

// Now type-safe!
const { page, query } = useSearch()  // Type: { page: number, query?: string }
```

**Recommendation:** Use Zod for search param validation if using search heavily.

### 8. React Query Dev Tools Performance ⚠️

**Issue:** React Query dev tools can impact performance in development.

**Solution:** Only load in development:
```typescript
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'

<QueryClientProvider client={queryClient}>
  <App />
  {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
</QueryClientProvider>
```

**Note:** Dev tools are tree-shakeable and won't be in production bundle.

### 9. Router Dev Tools Placement ⚠️

**Issue:** Router dev tools must be inside RouterProvider.

**Wrong:**
```typescript
<RouterProvider router={router} />
<TanStackRouterDevtools router={router} />  // ❌ Outside provider
```

**Correct:**
```typescript
// In __root.tsx
export const Route = createRootRoute({
  component: () => (
    <>
      <Outlet />
      <TanStackRouterDevtools />  // ✅ Inside root route
    </>
  ),
})
```

### 10. Avoid Mixing Router Types ⚠️

**Issue:** Don't mix TanStack Router with React Router.

**Why:**
- Different routing paradigms
- Type conflicts
- Duplicate router logic
- Bundle size bloat

**Choose one:** TanStack Router for new projects (better TypeScript support, modern features).

## Installation Summary

### Prerequisites (Already Met)
- ✅ Vite 7.2.2 installed
- ✅ React 19.2.0 with `createRoot`
- ✅ TypeScript 5.9.3 (meets v5.3+ requirement)
- ✅ Path aliases configured (@/)
- ✅ @types/node installed

### Required Dependencies

**TanStack Query:**
```bash
yarn add @tanstack/react-query
yarn add -D @tanstack/react-query-devtools
```

**TanStack Router:**
```bash
yarn add @tanstack/react-router
yarn add -D @tanstack/router-plugin
yarn add -D @tanstack/router-devtools
```

**Optional (for search param validation):**
```bash
yarn add zod @tanstack/zod-adapter
```

### Configuration Files to Modify

1. **vite.config.ts** - Add TanStack Router plugin (BEFORE react plugin)
2. **src/main.tsx** - Wrap with providers (QueryClientProvider + RouterProvider)
3. **package.json** - Updated by yarn with new dependencies

### Configuration Files to Create

1. **src/lib/queryClient.ts** - QueryClient configuration
2. **src/lib/router.tsx** - Router setup and configuration
3. **src/routes/__root.tsx** - Root layout route
4. **src/routes/index.tsx** - Home page route
5. **src/routes/about.tsx** - Example test route

### Auto-Generated Files

1. **routeTree.gen.ts** - Generated by router plugin (DO commit to git)

## Testing Strategy

Based on the instructions, testing should verify:

### 1. React Query Functionality
**Test:** Add dummy query hook that fetches mock data

Create hook:
```typescript
// src/hooks/useExampleQuery.ts
export function useExampleQuery() {
  return useQuery({
    queryKey: ['example'],
    queryFn: async () => {
      await new Promise(resolve => setTimeout(resolve, 1000))
      return { message: 'Hello from React Query!', timestamp: Date.now() }
    },
  })
}
```

Use in route:
```typescript
// src/routes/index.tsx
const { data, isLoading } = useExampleQuery()

if (isLoading) return <div>Loading...</div>
return <div>{data?.message}</div>
```

**Verification:**
- ✅ Loading state shows initially
- ✅ Data appears after 1 second
- ✅ Dev tools show query in cache
- ✅ Refresh page - data loads from cache (if within staleTime)

### 2. Router Navigation
**Test:** Navigate between routes without page reload

Create test routes:
- `/` - Home page
- `/about` - About page

Add navigation:
```typescript
<Link to="/">Home</Link>
<Link to="/about">About</Link>
```

**Verification:**
- ✅ Click link - URL changes
- ✅ Content updates without full page reload
- ✅ Browser back/forward buttons work
- ✅ No network request for navigation (check DevTools Network tab)
- ✅ Router dev tools show current route

### 3. Integration Test
**Test:** Use query on routed page

Create route with data loading:
```typescript
const aboutRoute = createRoute({
  path: '/about',
  component: () => {
    const { data } = useExampleQuery()
    return <div>About page - {data?.message}</div>
  },
})
```

**Verification:**
- ✅ Navigate to /about
- ✅ Query executes
- ✅ Data displays
- ✅ Navigate away and back - data loads from cache

## Official Documentation References

- **TanStack Query v5:** https://tanstack.com/query/v5/docs/framework/react/installation
- **TanStack Router:** https://tanstack.com/router/latest/docs/framework/react/quick-start
- **Vite Plugin Setup:** https://tanstack.com/router/latest/docs/framework/react/installation/with-vite
- **React 19 Compatibility:** Both libraries support React 18+ (React 19 included)

## Version Compatibility Matrix

| Library | Version | React 19 | TypeScript 5.9 | Vite 7 |
|---------|---------|----------|----------------|---------|
| TanStack Query | v5.90.7+ | ✅ | ✅ | ✅ |
| TanStack Router | v1.99+ | ✅ | ✅ | ✅ |
| React | 19.2.0 | ✅ | ✅ | ✅ |
| TypeScript | 5.9.3 | ✅ | ✅ | ✅ |
| Vite | 7.2.2 | ✅ | ✅ | ✅ |

All dependencies are fully compatible with each other.
