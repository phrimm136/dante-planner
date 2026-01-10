import { createRouter, createRootRoute, createRoute, lazyRouteComponent, stripSearchParams } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { z } from 'zod'
import { zodValidator } from '@tanstack/zod-adapter'
import { GlobalLayout } from '@/components/GlobalLayout'
import { RouteErrorComponent } from '@/components/common/RouteErrorComponent'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
// NotFoundPage is eagerly loaded as it's used as the default 404 component
import NotFoundPage from '@/routes/NotFoundPage'

// Note: All route components are lazy loaded for code splitting
// Each route will load its JS bundle only when navigated to
// pendingComponent shows while the JS bundle loads (before component mounts)

// ============================================================================
// Search Param Schemas
// ============================================================================

/**
 * Default values for MD user search params
 * Used by stripSearchParams middleware to hide defaults from URL
 */
const mdUserDefaults = {
  page: 0,
}

/**
 * Search params schema for /planner/md (personal planners)
 * Minimal params - category filter, pagination, and search
 */
const mdUserSearchSchema = z.object({
  category: z.enum(['5F', '10F', '15F']).optional(),
  page: z.coerce.number().int().min(0).default(mdUserDefaults.page),
  q: z.string().max(200).optional(),
})

/**
 * Default values for MD gesellschaft search params
 * Used by stripSearchParams middleware to hide defaults from URL
 */
const mdGesellschaftDefaults = {
  page: 0,
  mode: 'published' as const,
}

/**
 * Search params schema for /planner/md/gesellschaft (community planners)
 * Includes mode parameter for all published vs recommended
 */
const mdGesellschaftSearchSchema = z.object({
  category: z.enum(['5F', '10F', '15F']).optional(),
  page: z.coerce.number().int().min(0).default(mdGesellschaftDefaults.page),
  mode: z.enum(['published', 'best']).default(mdGesellschaftDefaults.mode),
  q: z.string().max(200).optional(),
})

// ============================================================================
// Pending Components (Route Loading States)
// ============================================================================

/**
 * Pending components show while the route's JS bundle loads (lazyRouteComponent).
 * These wrap skeleton components with the same page structure (title, description)
 * as the actual page for a seamless loading experience.
 */

// Identity list page loading state
const IdentityPagePending = () => (
  <div className="container mx-auto p-8">
    <ListPageSkeleton preset="identity" />
  </div>
)

// Identity detail page loading state
const IdentityDetailPagePending = () => <DetailPageSkeleton preset="identity" />

// EGO list page loading state
const EGOPagePending = () => (
  <div className="container mx-auto p-8">
    <ListPageSkeleton preset="ego" />
  </div>
)

// EGO detail page loading state
const EGODetailPagePending = () => <DetailPageSkeleton preset="ego" />

// EGO Gift list page loading state
const EGOGiftPagePending = () => (
  <div className="container mx-auto p-8">
    <ListPageSkeleton preset="egoGift" />
  </div>
)

// EGO Gift detail page loading state
const EGOGiftDetailPagePending = () => <DetailPageSkeleton preset="egoGift" />

// Root route - contains layout for all routes
const rootRoute = createRootRoute({
  component: () => (
    <>
      <GlobalLayout>
        <Outlet />
      </GlobalLayout>
      {/* Router dev tools - only in development */}
      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </>
  ),
})

// Home route - path: "/"
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: lazyRouteComponent(() => import('@/routes/HomePage')),
})

// About route - path: "/about" (for testing navigation)
const aboutRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/about',
  component: lazyRouteComponent(() => import('@/routes/AboutPage')),
})

// Info route - path: "/info" (In-Game Info page)
const infoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/info',
  component: lazyRouteComponent(() => import('@/routes/InfoPage')),
})

// Planner route - path: "/planner" (Planner page)
const plannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner',
  component: lazyRouteComponent(() => import('@/routes/PlannerPage')),
})

// Planner MD route - path: "/planner/md" (Personal planners)
const plannerMDRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDPage')),
  validateSearch: zodValidator(mdUserSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdUserDefaults)],
  },
})

// Planner MD Gesellschaft route - path: "/planner/md/gesellschaft" (Community planners)
const plannerMDGesellschaftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/gesellschaft',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDGesellschaftPage')),
  validateSearch: zodValidator(mdGesellschaftSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdGesellschaftDefaults)],
  },
})

// Planner MD New route - path: "/planner/md/new" (Create new MD planner)
const plannerMDNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/new',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDNewPage')),
})

// Planner MD Detail route - path: "/planner/md/$id" (View planner)
const plannerMDDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDDetailPage')),
})

// Planner MD Edit route - path: "/planner/md/$id/edit" (Edit planner)
const plannerMDEditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id/edit',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDEditPage')),
})

// Extraction Planner route - path: "/planner/extraction" (Extraction probability calculator)
const extractionPlannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/extraction',
  component: lazyRouteComponent(() => import('@/routes/ExtractionPlannerPage')),
})

// Community route - path: "/community" (Community page)
const communityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/community',
  component: lazyRouteComponent(() => import('@/routes/CommunityPage')),
})

// Identity route - path: "/identity" (Identity browser page)
const identityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity',
  component: lazyRouteComponent(() => import('@/routes/IdentityPage')),
  pendingComponent: IdentityPagePending,
})

// Identity detail route - path: "/identity/$id" (Identity detail page)
const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: lazyRouteComponent(() => import('@/routes/IdentityDetailPage')),
  pendingComponent: IdentityDetailPagePending,
})

// EGO route - path: "/ego" (EGO browser page)
const egoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego',
  component: lazyRouteComponent(() => import('@/routes/EGOPage')),
  pendingComponent: EGOPagePending,
})

// EGO detail route - path: "/ego/$id" (EGO detail page)
const egoDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego/$id',
  component: lazyRouteComponent(() => import('@/routes/EGODetailPage')),
  pendingComponent: EGODetailPagePending,
})

// EGO Gift route - path: "/ego-gift" (EGO Gift browser page)
const egoGiftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift',
  component: lazyRouteComponent(() => import('@/routes/EGOGiftPage')),
  pendingComponent: EGOGiftPagePending,
})

// EGO Gift detail route - path: "/ego-gift/$id" (EGO Gift detail page)
const egoGiftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift/$id',
  component: lazyRouteComponent(() => import('@/routes/EGOGiftDetailPage')),
  pendingComponent: EGOGiftDetailPagePending,
})

// Settings route - path: "/settings" (User settings page)
const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: lazyRouteComponent(() => import('@/routes/SettingsPage')),
})

// Google OAuth callback route - path: "/auth/callback/google"
const googleCallbackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/auth/callback/google',
  component: lazyRouteComponent(() => import('@/routes/auth/callback/google')),
})

// Create route tree
// Note: TanStack Router handles route specificity automatically
// More specific routes like /planner/md/new will match before /planner/md
const routeTree = rootRoute.addChildren([
  indexRoute,
  aboutRoute,
  infoRoute,
  identityRoute,
  identityDetailRoute,
  egoRoute,
  egoDetailRoute,
  egoGiftRoute,
  egoGiftDetailRoute,
  plannerRoute,
  plannerMDRoute,
  plannerMDGesellschaftRoute,
  plannerMDNewRoute,
  plannerMDDetailRoute,
  plannerMDEditRoute,
  extractionPlannerRoute,
  communityRoute,
  settingsRoute,
  googleCallbackRoute,
])

// Create and export router instance
export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: NotFoundPage,
  defaultErrorComponent: RouteErrorComponent,
  // Show pending component immediately on navigation (no delay)
  defaultPendingMs: 0,
  // Minimum time to show pending component (prevents flash on very fast loads)
  defaultPendingMinMs: 200,
})

// Register router for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
