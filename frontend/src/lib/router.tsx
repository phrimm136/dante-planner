import { createRouter, createRootRoute, createRoute, lazyRouteComponent, stripSearchParams, HeadContent } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { z } from 'zod'
import { zodValidator } from '@tanstack/zod-adapter'
import { GlobalLayout } from '@/components/GlobalLayout'
import i18n from '@/lib/i18n'
import { ApiClient } from '@/lib/api'
import { storage } from '@/lib/storage'
import { PLANNER_STORAGE_KEYS } from '@/lib/constants'
import { RouteErrorComponent } from '@/components/common/RouteErrorComponent'
import { ListPageSkeleton } from '@/components/common/ListPageSkeleton'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
// NotFoundPage is eagerly loaded as it's used as the default 404 component
import NotFoundPage from '@/routes/NotFoundPage'

// Note: All route components are lazy loaded for code splitting
// Each route will load its JS bundle only when navigated to
// pendingComponent shows while the JS bundle loads (before component mounts)

/** Helper to create page title with site suffix */
const pageTitle = (key: string, ns = 'common') => `${i18n.t(key, { ns })} | Dante's Planner`

/** Load planner title from IndexedDB for route head */
async function loadPlannerTitle(plannerId: string): Promise<string> {
  try {
    const deviceId = await storage.getItem(PLANNER_STORAGE_KEYS.DEVICE_ID)
    if (!deviceId) return plannerId

    const key = `${PLANNER_STORAGE_KEYS.PLANNER}:${PLANNER_STORAGE_KEYS.MD}:${deviceId}:${plannerId}`
    const rawData = await storage.getItem(key)
    if (!rawData) return plannerId

    const parsed = JSON.parse(rawData)
    return parsed?.metadata?.title || plannerId
  } catch {
    return plannerId
  }
}

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
  head: () => ({
    meta: [
      { title: "Dante's Planner" },
      { name: 'description', content: 'Game planning tool for Limbus Company. Browse Identity, EGO, and EGO Gift databases. Plan Mirror Dungeon runs and build extraction teams.' },
    ],
  }),
  component: () => (
    <>
      <HeadContent />
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
  head: () => ({
    meta: [{ title: "Dante's Planner - Limbus Company Guide" }],
  }),
})

// Planner route - path: "/planner" (Planner page)
const plannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner',
  component: lazyRouteComponent(() => import('@/routes/PlannerPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.planner.title') }],
  }),
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
  head: () => ({
    meta: [{ title: pageTitle('header.nav.mirrorDungeon') }],
  }),
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
  head: () => ({
    meta: [{ title: pageTitle('pages.home.communityPlans.title') }],
  }),
})

// Planner MD Gesellschaft Detail route - path: "/planner/md/gesellschaft/$id" (View published planner)
const plannerMDGesellschaftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/gesellschaft/$id',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDGesellschaftDetailPage')),
  loader: async ({ params }) => {
    const data = await ApiClient.get(`/api/planner/md/published/${params.id}`)
    return { title: (data as { title?: string }).title ?? params.id }
  },
  head: ({ loaderData }) => ({
    meta: [{ title: `${loaderData?.title ?? 'Planner'} | Dante's Planner` }],
  }),
})

// Planner MD New route - path: "/planner/md/new" (Create new MD planner)
const plannerMDNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/new',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDNewPage')),
  head: () => ({
    meta: [{ title: pageTitle('planner.newPlanner', 'planner') }],
  }),
})

// Deck Builder route - path: "/planner/deck" (Standalone deck builder)
const deckBuilderRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/deck',
  component: lazyRouteComponent(() => import('@/routes/DeckBuilderPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.deckBuilder') }],
  }),
})

// Planner MD Detail route - path: "/planner/md/$id" (View planner)
const plannerMDDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDDetailPage')),
  loader: async ({ params }) => {
    const title = await loadPlannerTitle(params.id)
    return { title }
  },
  head: ({ loaderData }) => ({
    meta: [{ title: `${loaderData?.title ?? 'Planner'} | Dante's Planner` }],
  }),
})

// Planner MD Edit route - path: "/planner/md/$id/edit" (Edit planner)
const plannerMDEditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id/edit',
  component: lazyRouteComponent(() => import('@/routes/PlannerMDEditPage')),
  loader: async ({ params }) => {
    const title = await loadPlannerTitle(params.id)
    return { title }
  },
  head: ({ loaderData }) => ({
    meta: [{ title: `${i18n.t('pages.edit.title', { ns: 'planner' })} - ${loaderData?.title ?? 'Planner'} | Dante's Planner` }],
  }),
})

// Extraction Planner route - path: "/planner/extraction" (Extraction probability calculator)
const extractionPlannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/extraction',
  component: lazyRouteComponent(() => import('@/routes/ExtractionPlannerPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.extraction') }],
  }),
})

// Identity route - path: "/identity" (Identity browser page)
const identityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity',
  component: lazyRouteComponent(() => import('@/routes/IdentityPage')),
  pendingComponent: IdentityPagePending,
  head: () => ({
    meta: [{ title: pageTitle('header.nav.identity') }],
  }),
})

// Identity detail route - path: "/identity/$id" (Identity detail page)
const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: lazyRouteComponent(() => import('@/routes/IdentityDetailPage')),
  pendingComponent: IdentityDetailPagePending,
  loader: async ({ params }) => {
    const module = await import(`@static/i18n/${i18n.language}/identity/${params.id}.json`)
    const name = (module.default as { name?: string }).name?.replace(/\n/g, ' ') ?? params.id
    return { name }
  },
  head: ({ loaderData }) => ({
    meta: [{ title: `${loaderData?.name ?? 'Identity'} | Dante's Planner` }],
  }),
})

// EGO route - path: "/ego" (EGO browser page)
const egoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego',
  component: lazyRouteComponent(() => import('@/routes/EGOPage')),
  pendingComponent: EGOPagePending,
  head: () => ({
    meta: [{ title: pageTitle('header.nav.ego') }],
  }),
})

// EGO detail route - path: "/ego/$id" (EGO detail page)
const egoDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego/$id',
  component: lazyRouteComponent(() => import('@/routes/EGODetailPage')),
  pendingComponent: EGODetailPagePending,
  loader: async ({ params }) => {
    const module = await import(`@static/i18n/${i18n.language}/ego/${params.id}.json`)
    const name = (module.default as { name?: string }).name?.replace(/\n/g, ' ') ?? params.id
    return { name }
  },
  head: ({ loaderData }) => ({
    meta: [{ title: `${loaderData?.name ?? 'EGO'} | Dante's Planner` }],
  }),
})

// EGO Gift route - path: "/ego-gift" (EGO Gift browser page)
const egoGiftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift',
  component: lazyRouteComponent(() => import('@/routes/EGOGiftPage')),
  pendingComponent: EGOGiftPagePending,
  head: () => ({
    meta: [{ title: pageTitle('header.nav.egoGift') }],
  }),
})

// EGO Gift detail route - path: "/ego-gift/$id" (EGO Gift detail page)
const egoGiftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift/$id',
  component: lazyRouteComponent(() => import('@/routes/EGOGiftDetailPage')),
  pendingComponent: EGOGiftDetailPagePending,
  loader: async ({ params }) => {
    const module = await import(`@static/i18n/${i18n.language}/egoGift/${params.id}.json`)
    const name = (module.default as { name?: string }).name ?? params.id
    return { name }
  },
  head: ({ loaderData }) => ({
    meta: [{ title: `${loaderData?.name ?? 'EGO Gift'} | Dante's Planner` }],
  }),
})

// Settings route - path: "/settings" (User settings page)
const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: lazyRouteComponent(() => import('@/routes/SettingsPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.settings.settings') }],
  }),
})

// Privacy Policy route - path: "/privacy"
const privacyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/privacy',
  component: lazyRouteComponent(() => import('@/routes/PrivacyPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.privacy.title') }],
  }),
})

// Terms of Service route - path: "/terms"
const termsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/terms',
  component: lazyRouteComponent(() => import('@/routes/TermsPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.terms.title') }],
  }),
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
  identityRoute,
  identityDetailRoute,
  egoRoute,
  egoDetailRoute,
  egoGiftRoute,
  egoGiftDetailRoute,
  plannerRoute,
  plannerMDRoute,
  plannerMDGesellschaftRoute,
  plannerMDGesellschaftDetailRoute,
  plannerMDNewRoute,
  deckBuilderRoute,
  plannerMDDetailRoute,
  plannerMDEditRoute,
  extractionPlannerRoute,
  settingsRoute,
  privacyRoute,
  termsRoute,
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
