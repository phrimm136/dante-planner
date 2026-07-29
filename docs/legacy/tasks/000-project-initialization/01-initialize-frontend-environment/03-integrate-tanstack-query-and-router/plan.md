# Implementation Plan

## Task Overview

Integrate TanStack Query (v5) and TanStack Router (v1) into the Vite + React 19 + TypeScript frontend application. This will provide:

- **Data Fetching & Caching**: TanStack Query for managing server state with automatic caching, background updates, and request deduplication
- **Type-Safe Routing**: TanStack Router with full TypeScript inference for routes, params, and search parameters
- **Developer Tools**: Built-in dev tools for debugging queries and routing
- **Production Ready**: Optimized builds with code-splitting support

**Key Approach:**
- Use code-based routing (simpler initial setup than file-based)
- Configure Vite plugin correctly (router plugin BEFORE React plugin)
- Create stable singleton instances (queryClient and router at module level)
- Add dev tools for development experience
- Include test routes and queries to verify functionality

## Steps to Implementation

### 1. Install TanStack Dependencies

Install all required packages for TanStack Query and Router.

**Commands:**
```bash
cd frontend
yarn add @tanstack/react-query @tanstack/react-router
yarn add -D @tanstack/react-query-devtools @tanstack/router-plugin @tanstack/router-devtools
```

**What this installs:**
- `@tanstack/react-query` - Core query/mutation library (v5.90.7+)
- `@tanstack/react-router` - Core routing library (v1.99+)
- `@tanstack/react-query-devtools` - Dev tools for debugging queries
- `@tanstack/router-plugin` - Vite plugin for router (REQUIRED)
- `@tanstack/router-devtools` - Dev tools for debugging routes

**Expected output:**
- Dependencies added to package.json
- yarn.lock updated with new packages

### 2. Configure Vite with TanStack Router Plugin

Update vite.config.ts to include the TanStack Router plugin. **CRITICAL: Plugin must come BEFORE React plugin.**

**File:** `frontend/vite.config.ts`

**Changes:**
```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { TanStackRouterVite } from '@tanstack/router-plugin/vite'
import path from 'path'

export default defineConfig({
  plugins: [
    TanStackRouterVite(),  // ← MUST be first (before react())
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
```

**Why order matters:**
- Router plugin generates route tree TypeScript code
- React plugin needs to process that generated code
- Wrong order causes "Cannot find module" errors

### 3. Create Query Client Configuration

Create the QueryClient singleton with sensible default options.

**File:** `frontend/src/lib/queryClient.ts` (NEW FILE)

**Content:**
```typescript
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000, // 1 minute - data is fresh for 1 min
      gcTime: 5 * 60 * 1000, // 5 minutes - cache for 5 min (formerly cacheTime)
      retry: 1, // Retry failed queries once
      refetchOnWindowFocus: false, // Don't refetch when window regains focus
    },
  },
})
```

**Configuration explained:**
- `staleTime`: How long before data is considered stale (triggers background refetch)
- `gcTime`: How long to keep unused data in cache
- `retry`: Number of retry attempts for failed queries
- `refetchOnWindowFocus`: Prevents unnecessary refetches when switching tabs

### 4. Create Router Configuration

Set up the router with root and test routes using code-based routing.

**File:** `frontend/src/lib/router.tsx` (NEW FILE)

**Content:**
```typescript
import { createRouter, createRootRoute, createRoute } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import HomePage from '@/routes/HomePage'
import AboutPage from '@/routes/AboutPage'

// Root route - contains layout for all routes
const rootRoute = createRootRoute({
  component: () => (
    <div className="min-h-screen bg-background text-foreground">
      <Outlet />
    </div>
  ),
})

// Home route - path: "/"
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: HomePage,
})

// About route - path: "/about" (for testing navigation)
const aboutRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/about',
  component: AboutPage,
})

// Create route tree
const routeTree = rootRoute.addChildren([indexRoute, aboutRoute])

// Create and export router instance
export const router = createRouter({ routeTree })

// Register router for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
```

**Structure explained:**
- `rootRoute`: Top-level layout, renders `<Outlet />` for nested routes
- `indexRoute`: Home page at `/`
- `aboutRoute`: Test page at `/about`
- Type registration enables full TypeScript inference

### 5. Create Route Components

Create the actual page components that will be rendered by the router.

#### HomePage Component

**File:** `frontend/src/routes/HomePage.tsx` (NEW FILE)

**Content:**
```typescript
import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { useExampleQuery } from '@/hooks/useExampleQuery'

export default function HomePage() {
  const { data, isLoading, error } = useExampleQuery()

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center space-y-6">
        <h1 className="text-4xl font-bold">Limbus Planner</h1>

        <div className="space-y-2">
          <p className="text-muted-foreground">
            TanStack Query and Router integrated!
          </p>

          {/* React Query Test */}
          <div className="p-4 border rounded-md">
            <h2 className="font-semibold mb-2">React Query Test:</h2>
            {isLoading && <p>Loading data...</p>}
            {error && <p className="text-destructive">Error: {error.message}</p>}
            {data && (
              <div>
                <p className="text-sm">{data.message}</p>
                <p className="text-xs text-muted-foreground">
                  Timestamp: {new Date(data.timestamp).toLocaleTimeString()}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Navigation Test */}
        <div className="space-x-2">
          <Button asChild>
            <Link to="/about">Go to About</Link>
          </Button>
        </div>
      </div>
    </div>
  )
}
```

#### AboutPage Component

**File:** `frontend/src/routes/AboutPage.tsx` (NEW FILE)

**Content:**
```typescript
import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { useExampleQuery } from '@/hooks/useExampleQuery'

export default function AboutPage() {
  const { data, isLoading } = useExampleQuery()

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center space-y-6">
        <h1 className="text-4xl font-bold">About Page</h1>

        <p className="text-muted-foreground">
          Testing TanStack Router navigation
        </p>

        {/* Verify query cache works across routes */}
        <div className="p-4 border rounded-md">
          <h2 className="font-semibold mb-2">Cached Query Data:</h2>
          {isLoading ? (
            <p>Loading...</p>
          ) : (
            <p className="text-sm">{data?.message}</p>
          )}
        </div>

        <div className="space-x-2">
          <Button asChild variant="outline">
            <Link to="/">Back to Home</Link>
          </Button>
        </div>
      </div>
    </div>
  )
}
```

### 6. Create Example Query Hook

Create a custom hook that uses React Query to fetch mock data for testing.

**File:** `frontend/src/hooks/useExampleQuery.ts` (NEW FILE)

**Content:**
```typescript
import { useQuery } from '@tanstack/react-query'

interface ExampleData {
  message: string
  timestamp: number
}

// Mock API function that simulates async data fetching
async function fetchExampleData(): Promise<ExampleData> {
  // Simulate network delay
  await new Promise(resolve => setTimeout(resolve, 1000))

  return {
    message: 'Hello from TanStack Query! Data is cached. ✨',
    timestamp: Date.now(),
  }
}

export function useExampleQuery() {
  return useQuery({
    queryKey: ['example'],
    queryFn: fetchExampleData,
  })
}
```

**How it works:**
- Simulates 1-second API delay
- Returns mock data with timestamp
- Query automatically caches result
- Navigate between pages to see cache in action

### 7. Update Main Entry Point

Update main.tsx to wrap the app with QueryClientProvider and RouterProvider.

**File:** `frontend/src/main.tsx`

**Replace entire content with:**
```typescript
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { RouterProvider } from '@tanstack/react-router'
import { queryClient } from '@/lib/queryClient'
import { router } from '@/lib/router'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      {/* Dev tools - only in development */}
      {import.meta.env.DEV && (
        <ReactQueryDevtools initialIsOpen={false} />
      )}
    </QueryClientProvider>
  </StrictMode>,
)
```

**Changes explained:**
- Removed `<App />` (now using router)
- Added `QueryClientProvider` wrapping everything
- Added `RouterProvider` with our router config
- Added `ReactQueryDevtools` for debugging (dev only)
- Dev tools automatically tree-shaken in production

**Note:** Router dev tools are added in the root route component, not here.

### 8. Create Hooks Directory

Create the hooks directory for organizing custom React Query hooks.

**Command:**
```bash
mkdir -p frontend/src/hooks
```

**Purpose:**
- Centralized location for custom hooks
- Keeps components clean
- Reusable query/mutation hooks

### 9. Create Routes Directory

Create the routes directory for page components.

**Command:**
```bash
mkdir -p frontend/src/routes
```

**Purpose:**
- Separates route components from other components
- Clear structure for pages
- Easy to find entry points

### 10. Verify Dev Server

Start the development server and verify everything works.

**Commands:**
```bash
yarn dev
```

**Verification checklist:**
- ✅ Dev server starts without errors
- ✅ No TypeScript errors
- ✅ Navigate to http://localhost:5173/
- ✅ Home page loads with "Loading data..." then shows cached message
- ✅ Click "Go to About" - navigates without page reload
- ✅ About page shows cached data (no loading state)
- ✅ Click "Back to Home" - returns to home
- ✅ React Query dev tools visible (floating icon)
- ✅ Can see query cache in dev tools

**Expected behavior:**
- First load: Shows loading, then data appears after 1 second
- Navigate to About: Data appears immediately (from cache)
- Navigate back to Home: Still cached
- Wait 1 minute: Data becomes stale, refetches in background

### 11. Test Production Build

Verify that production build works correctly.

**Commands:**
```bash
yarn build
```

**Expected output:**
- TypeScript compilation succeeds
- Vite build completes
- Router types generated correctly
- No errors or warnings
- Optimized bundles in `dist/`

**Build output should include:**
- Route code-splitting (if configured)
- Query client bundled
- Dev tools excluded from production
- Optimized CSS and JS

### 12. Optional: Add Router Dev Tools

Optionally add TanStack Router dev tools to the root route for debugging.

**File:** `frontend/src/lib/router.tsx` (MODIFY)

**Add import:**
```typescript
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
```

**Update root route:**
```typescript
const rootRoute = createRootRoute({
  component: () => (
    <div className="min-h-screen bg-background text-foreground">
      <Outlet />
      {/* Router dev tools - only in development */}
      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </div>
  ),
})
```

**Benefits:**
- See current route and params
- View route tree structure
- Inspect route matches
- Debug navigation issues

## Timeline

| Step | Task | Estimated Time |
|------|------|----------------|
| 1 | Install dependencies | 1-2 minutes |
| 2 | Configure Vite plugin | 1 minute |
| 3 | Create queryClient.ts | 1 minute |
| 4 | Create router.tsx | 2 minutes |
| 5 | Create route components (HomePage, AboutPage) | 3 minutes |
| 6 | Create useExampleQuery hook | 2 minutes |
| 7 | Update main.tsx | 2 minutes |
| 8-9 | Create directories | 1 minute |
| 10 | Test dev server | 3 minutes |
| 11 | Test production build | 2 minutes |
| 12 | Add router dev tools (optional) | 1 minute |
| **Total** | | **~20 minutes** |

## Success Criteria

- ✅ TanStack Query installed and configured
- ✅ TanStack Router installed with Vite plugin
- ✅ Router plugin correctly ordered (before React plugin)
- ✅ QueryClient created with sensible defaults
- ✅ Router created with root, home, and about routes
- ✅ Route components created (HomePage, AboutPage)
- ✅ Example query hook created and working
- ✅ main.tsx updated with both providers
- ✅ Navigation between routes works without reload
- ✅ Query caching works across route changes
- ✅ Dev tools visible in development
- ✅ Dev server runs without errors
- ✅ Production build completes successfully
- ✅ No TypeScript errors

## Potential Issues & Mitigations

### Issue: Vite Plugin Order Error

**Symptoms:**
```
Error: Cannot find module '@tanstack/router'
Type error: Cannot find name 'routeTree'
```

**Cause:** React plugin is before Router plugin in vite.config.ts

**Mitigation:**
Ensure plugin order is:
```typescript
plugins: [
  TanStackRouterVite(),  // ← First
  react(),               // ← Second
  tailwindcss(),
]
```

### Issue: QueryClient Re-creation

**Symptoms:** Infinite re-renders, cache not persisting

**Cause:** QueryClient created inside component

**Mitigation:**
Always create QueryClient at module level:
```typescript
// ✅ Correct - in lib/queryClient.ts
export const queryClient = new QueryClient()

// ❌ Wrong - inside component
function App() {
  const queryClient = new QueryClient()
}
```

### Issue: Router Not Found Type Errors

**Symptoms:** Router types are `any`, no autocomplete

**Cause:** Module augmentation not included

**Mitigation:**
Include type registration:
```typescript
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
```

### Issue: Routes Not Updating

**Symptoms:** Changed route file but routes not updating

**Cause:** Dev server needs restart for route changes

**Mitigation:**
Restart dev server:
```bash
# Stop server (Ctrl+C)
yarn dev
```

### Issue: Import Errors for Route Components

**Symptoms:**
```
Cannot find module '@/routes/HomePage'
```

**Cause:** Files not created yet or incorrect path alias

**Mitigation:**
1. Verify files exist at correct paths
2. Check path alias in vite.config.ts, tsconfig.json, tsconfig.app.json
3. Restart TypeScript server in IDE

### Issue: Query Not Caching

**Symptoms:** Query refetches every time, no cache

**Cause:** Different query keys or staleTime too low

**Mitigation:**
- Use consistent query keys: `['example']` not `['example', Math.random()]`
- Increase staleTime if needed
- Check React Query dev tools to see cache

## File Structure Summary

### Files to Modify
1. `vite.config.ts` - Add TanStack Router plugin
2. `main.tsx` - Add QueryClientProvider and RouterProvider
3. `package.json` - Updated by yarn (dependencies)

### Files to Create
1. `src/lib/queryClient.ts` - QueryClient configuration
2. `src/lib/router.tsx` - Router setup and routes
3. `src/routes/HomePage.tsx` - Home page component
4. `src/routes/AboutPage.tsx` - About page component
5. `src/hooks/useExampleQuery.ts` - Example query hook

### Directories to Create
1. `src/routes/` - Page components directory
2. `src/hooks/` - Custom hooks directory

### Files NOT to Create
- ❌ No `routeTree.gen.ts` needed (code-based routing doesn't use it)
- ❌ No `__root.tsx` file (we define root route in router.tsx)

## Next Steps (After Completion)

1. **Add More Routes** - Create routes for actual application features
2. **Add Real API Calls** - Replace mock data with actual API endpoints
3. **Add Route Loaders** - Prefetch data before route renders
4. **Add Error Boundaries** - Handle routing and query errors gracefully
5. **Add Loading States** - Better loading UI for route transitions
6. **Add Route Guards** - Protect routes based on authentication
7. **Configure Code Splitting** - Lazy load routes for better performance
8. **Add Search Params** - Handle URL search parameters with validation
9. **Add Mutations** - Use TanStack Query for POST/PUT/DELETE operations
10. **Add Optimistic Updates** - Update UI before API responds

## Testing Checklist

### Manual Testing

**React Query:**
- [ ] Query shows loading state initially
- [ ] Data appears after 1 second
- [ ] Timestamp shows current time
- [ ] Navigate to About - data loads from cache (instant)
- [ ] React Query dev tools show query in cache
- [ ] Wait 60 seconds - query refetches in background

**TanStack Router:**
- [ ] Click "Go to About" - URL changes to `/about`
- [ ] Content updates without page reload
- [ ] No network request in DevTools Network tab
- [ ] Click "Back to Home" - URL changes to `/`
- [ ] Browser back/forward buttons work
- [ ] Direct navigation to `/about` works (paste URL)

**Dev Tools:**
- [ ] React Query dev tools icon visible in corner
- [ ] Can open/close query dev tools
- [ ] See query key: `['example']`
- [ ] See query state: success/loading/error
- [ ] Router dev tools visible (if added)

**Production Build:**
- [ ] `yarn build` completes without errors
- [ ] No TypeScript errors
- [ ] Output files in `dist/`
- [ ] Dev tools not included in production bundle

## Notes

- This implementation uses **code-based routing** for simplicity
- File-based routing can be added later by creating `src/routes/` directory with route files
- Query configuration can be adjusted based on API requirements
- Both dev tools are tree-shaken in production (no bundle bloat)
- Router plugin order is critical - must be before React plugin
