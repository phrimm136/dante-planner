import { createRouter, createRootRoute, createRoute } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { z } from 'zod'
import HomePage from '@/routes/HomePage'
import AboutPage from '@/routes/AboutPage'
import InfoPage from '@/routes/InfoPage'
import IdentityPage from '@/routes/IdentityPage'
import IdentityDetailPage from '@/routes/IdentityDetailPage'
import EGOPage from '@/routes/EGOPage'
import EGODetailPage from '@/routes/EGODetailPage'
import EGOGiftPage from '@/routes/EGOGiftPage'
import EGOGiftDetailPage from '@/routes/EGOGiftDetailPage'
import PlannerPage from '@/routes/PlannerPage'
import PlannerListPage from '@/routes/PlannerListPage'
import PlannerMDNewPage from '@/routes/PlannerMDNewPage'
import ExtractionPlannerPage from '@/routes/ExtractionPlannerPage'
import CommunityPage from '@/routes/CommunityPage'
import GoogleCallback from '@/routes/auth/callback/google'
import NotFoundPage from '@/routes/NotFoundPage'
import { GlobalLayout } from '@/components/GlobalLayout'
import { RouteErrorComponent } from '@/components/common/RouteErrorComponent'

// ============================================================================
// Search Param Schemas
// ============================================================================

/**
 * Search params schema for /planner/md (list page)
 * Validates and transforms URL search params
 */
const plannerListSearchSchema = z.object({
  view: z.enum(['my-plans', 'community']).optional().default('community'),
  filter: z.enum(['all', 'recommended']).optional().default('all'),
  category: z.enum(['5F', '10F', '15F']).optional(),
  page: z.coerce.number().int().min(0).optional().default(0),
  sort: z.enum(['recent', 'popular', 'votes']).optional().default('recent'),
  q: z.string().optional(),
})

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
  component: HomePage,
})

// About route - path: "/about" (for testing navigation)
const aboutRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/about',
  component: AboutPage,
})

// Info route - path: "/info" (In-Game Info page)
const infoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/info',
  component: InfoPage,
})

// Planner route - path: "/planner" (Planner page)
const plannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner',
  component: PlannerPage,
})

// Planner MD List route - path: "/planner/md" (Planner list page)
const plannerMDListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md',
  component: PlannerListPage,
  validateSearch: plannerListSearchSchema,
})

// Planner MD New route - path: "/planner/md/new" (Create new MD planner)
const plannerMDNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/new',
  component: PlannerMDNewPage,
})

// Extraction Planner route - path: "/planner/extraction" (Extraction probability calculator)
const extractionPlannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/extraction',
  component: ExtractionPlannerPage,
})

// Community route - path: "/community" (Community page)
const communityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/community',
  component: CommunityPage,
})

// Identity route - path: "/identity" (Identity browser page)
const identityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity',
  component: IdentityPage,
})

// Identity detail route - path: "/identity/$id" (Identity detail page)
const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: IdentityDetailPage,
})

// EGO route - path: "/ego" (EGO browser page)
const egoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego',
  component: EGOPage,
})

// EGO detail route - path: "/ego/$id" (EGO detail page)
const egoDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego/$id',
  component: EGODetailPage,
})

// EGO Gift route - path: "/ego-gift" (EGO Gift browser page)
const egoGiftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift',
  component: EGOGiftPage,
})

// EGO Gift detail route - path: "/ego-gift/$id" (EGO Gift detail page)
const egoGiftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift/$id',
  component: EGOGiftDetailPage,
})

// Google OAuth callback route - path: "/auth/callback/google"
const googleCallbackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/auth/callback/google',
  component: GoogleCallback,
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
  plannerMDListRoute,
  plannerMDNewRoute,
  extractionPlannerRoute,
  communityRoute,
  googleCallbackRoute,
])

// Create and export router instance
export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: NotFoundPage,
  defaultErrorComponent: RouteErrorComponent,
})

// Register router for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
