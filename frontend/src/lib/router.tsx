import {
  createRouter,
  createRootRoute,
  createRoute,
  lazyRouteComponent,
  stripSearchParams,
  HeadContent,
} from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import { z } from 'zod'
import { zodValidator } from '@tanstack/zod-adapter'
import { GlobalLayout } from '@/components/layout/GlobalLayout'
import i18n from '@/lib/i18n'
import { untitledPlannerTitle } from '@/pages/planner/lib/loadPlannerTitle'
import { MD_CATEGORIES } from '@/shared/gameData'
import {
  loadPublishedPlanner,
  loadPlannerTitleRoute,
  loadIdentityName,
  loadEgoName,
  loadEgoGiftName,
  loadThemePackName,
  loadKeywordName,
  loadAbEventTitle,
} from '@/lib/routeLoaders'
import { syncTitleOnLanguageChange } from '@/lib/routerTitle'
import { RouteErrorComponent } from '@/components/feedback/RouteErrorComponent'
import { RoutePendingFallback } from '@/components/feedback/RoutePendingFallback'

// NotFoundPage is eagerly loaded as it's used as the default 404 component
import NotFoundPage from '@/components/feedback/NotFoundPage'

// Note: All route components are lazy loaded for code splitting
// Each route will load its JS bundle only when navigated to
// pendingComponent shows while the JS bundle loads (before component mounts)

/** Helper to create page title with site suffix */
const pageTitle = (key: string, ns = 'common') => `${i18n.t(key, { ns })} | Dante's Planner`

/**
 * Head meta for a detail route. `fallback` covers the window before the route's
 * loader has resolved, when `head()` still runs with no loader data.
 */
const detailHead = (title: string | undefined, fallback: string) => ({
  meta: [{ title: `${title ?? fallback} | Dante's Planner` }],
})

// ============================================================================
// Search Param Schemas
// ============================================================================

const mdUserDefaults = {
  page: 0,
}

/**
 * Search params schema for /planner/md (personal planners)
 * Minimal params - category filter, pagination, and search
 */
const mdUserSearchSchema = z.object({
  category: z.enum(MD_CATEGORIES).optional(),
  page: z.coerce.number().int().min(0).default(mdUserDefaults.page),
  q: z.string().max(200).optional(),
  keyword: z.string().max(500).optional(),
  identity: z.string().max(500).optional(),
  ego: z.string().max(500).optional(),
  gift: z.string().max(500).optional(),
  themePack: z.string().max(500).optional(),
})

const mdGesellschaftDefaults = {
  page: 0,
  mode: 'published' as const,
}

/**
 * Search params schema for /planner/md/gesellschaft (community planners)
 * Includes mode parameter for all published vs recommended
 */
const mdGesellschaftSearchSchema = z.object({
  category: z.enum(MD_CATEGORIES).optional(),
  page: z.coerce.number().int().min(0).default(mdGesellschaftDefaults.page),
  mode: z.enum(['published', 'best']).default(mdGesellschaftDefaults.mode),
  q: z.string().max(200).optional(),
  keyword: z.string().max(500).optional(),
  identity: z.string().max(500).optional(),
  ego: z.string().max(500).optional(),
  gift: z.string().max(500).optional(),
  themePack: z.string().max(500).optional(),
})

// Root route - contains layout for all routes
function RootLayout() {
  return (
    <>
      <HeadContent />
      <GlobalLayout>
        <Outlet />
      </GlobalLayout>
      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </>
  )
}

const rootRoute = createRootRoute({
  head: () => ({
    meta: [
      { title: "Dante's Planner" },
      {
        name: 'description',
        content:
          'Game planning tool for Limbus Company. Browse Identity, E.G.O, and E.G.O Gift databases. Plan Mirror Dungeon runs and track current run state.',
      },
    ],
  }),
  component: RootLayout,
})

// Home route - path: "/"
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: lazyRouteComponent(() => import('@/pages/home/HomePage')),
  head: () => ({
    meta: [{ title: "Dante's Planner - Limbus Company Database and Planning Tool" }],
  }),
})

// Planner route - path: "/planner" (Planner page)
const plannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.planner.title') }],
  }),
})

// Planner MD route - path: "/planner/md" (Personal planners)
const plannerMDRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDPage')),
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
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDGesellschaftPage')),
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
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDGesellschaftDetailPage')),
  validateSearch: zodValidator(mdGesellschaftSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdGesellschaftDefaults)],
  },
  loader: loadPublishedPlanner,
  head: ({ loaderData }) => detailHead(loaderData?.title, untitledPlannerTitle()),
})

// Planner MD New route - path: "/planner/md/new" (Create new MD planner)
const plannerMDNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/new',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDNewPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.plannerMD.newPlan', 'planner') }],
  }),
})

// Deck Builder route - path: "/planner/deck" (Standalone deck builder)
const deckBuilderRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/deck',
  component: lazyRouteComponent(() => import('@/pages/planner/DeckBuilderPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.deckBuilder') }],
  }),
})

// Planner MD Detail route - path: "/planner/md/$id" (View planner)
const plannerMDDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDDetailPage')),
  validateSearch: zodValidator(mdUserSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdUserDefaults)],
  },
  loader: loadPlannerTitleRoute,
  head: ({ loaderData }) => detailHead(loaderData?.title, untitledPlannerTitle()),
})

// Planner MD Edit route - path: "/planner/md/$id/edit" (Edit planner)
const plannerMDEditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id/edit',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDEditPage')),
  loader: loadPlannerTitleRoute,
  head: ({ loaderData }) => ({
    meta: [
      {
        title: `${i18n.t('pages.edit.title', { ns: 'planner' })} - ${loaderData?.title ?? untitledPlannerTitle()} | Dante's Planner`,
      },
    ],
  }),
})

// Extraction Planner route - path: "/planner/extraction" (Extraction probability calculator)
const extractionPlannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/extraction',
  component: lazyRouteComponent(() => import('@/pages/extraction/ExtractionPlannerPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.extraction') }],
  }),
})

// Identity route - path: "/identity" (Identity browser page)
const identityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity',
  component: lazyRouteComponent(() => import('@/pages/identity/IdentityPage')),

  head: () => ({
    meta: [{ title: pageTitle('header.nav.identity') }],
  }),
})

// Identity detail route - path: "/identity/$id" (Identity detail page)
const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: lazyRouteComponent(() => import('@/pages/identity/IdentityDetailPage')),

  loader: loadIdentityName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'Identity'),
})

// EGO route - path: "/ego" (EGO browser page)
const egoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego',
  component: lazyRouteComponent(() => import('@/pages/ego/EGOPage')),

  head: () => ({
    meta: [{ title: pageTitle('header.nav.ego') }],
  }),
})

// EGO detail route - path: "/ego/$id" (EGO detail page)
const egoDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego/$id',
  component: lazyRouteComponent(() => import('@/pages/ego/EGODetailPage')),

  loader: loadEgoName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'EGO'),
})

// EGO Gift route - path: "/ego-gift" (EGO Gift browser page)
const egoGiftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift',
  component: lazyRouteComponent(() => import('@/pages/egoGift/EGOGiftPage')),

  head: () => ({
    meta: [{ title: pageTitle('header.nav.egoGift') }],
  }),
})

// EGO Gift detail route - path: "/ego-gift/$id" (EGO Gift detail page)
const egoGiftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift/$id',
  component: lazyRouteComponent(() => import('@/pages/egoGift/EGOGiftDetailPage')),

  loader: loadEgoGiftName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'EGO Gift'),
})

// Theme Pack route - path: "/theme-pack" (Theme Pack browser page)
const themePackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/theme-pack',
  component: lazyRouteComponent(() => import('@/pages/themePack/ThemePackPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.themePack') }],
  }),
})

// Theme Pack detail route - path: "/theme-pack/$id"
const themePackDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/theme-pack/$id',
  component: lazyRouteComponent(() => import('@/pages/themePack/ThemePackDetailPage')),
  loader: loadThemePackName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'Theme Pack'),
})

// Ab Event route - path: "/ab-event" (Abnormality Event browser page)
const abEventRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ab-event',
  component: lazyRouteComponent(() => import('@/pages/abEvent/AbEventPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.abEvent') }],
  }),
})

// Ab Event detail route - path: "/ab-event/$id"
const abEventDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ab-event/$id',
  component: lazyRouteComponent(() => import('@/pages/abEvent/AbEventDetailPage')),
  loader: loadAbEventTitle,
  head: ({ loaderData }) => detailHead(loaderData?.title, 'Dungeon Event'),
})

// Keyword route - path: "/keyword" (Keyword browser page)
const keywordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/keyword',
  component: lazyRouteComponent(() => import('@/pages/keyword/KeywordPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.keyword') }],
  }),
})

// Keyword detail route - path: "/keyword/$id" (Keyword detail page)
const keywordDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/keyword/$id',
  component: lazyRouteComponent(() => import('@/pages/keyword/KeywordDetailPage')),
  loader: loadKeywordName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'Keyword'),
})

// Settings route - path: "/settings" (User settings page)
const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: lazyRouteComponent(() => import('@/pages/settings/SettingsPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.settings.settings') }],
  }),
})

// Moderation dashboard route - path: "/moderation" (Moderator/Admin only)
const moderationRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/moderation',
  component: lazyRouteComponent(() => import('@/pages/moderator/ModeratorPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.moderator') }],
  }),
})

// Privacy Policy route - path: "/privacy"
const privacyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/privacy',
  component: lazyRouteComponent(() => import('@/pages/legal/PrivacyPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.privacy.title') }],
  }),
})

// Terms of Service route - path: "/terms"
const termsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/terms',
  component: lazyRouteComponent(() => import('@/pages/legal/TermsPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.terms.title') }],
  }),
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
  themePackRoute,
  themePackDetailRoute,
  abEventRoute,
  abEventDetailRoute,
  keywordRoute,
  keywordDetailRoute,
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
  moderationRoute,
  privacyRoute,
  termsRoute,
])

// Create and export router instance
/**
 * Custom search serializer that preserves commas in query strings.
 * Default encodeURIComponent encodes commas to %2C which is ugly for CSV params
 * like ?identity=10101,10102. Commas are valid in query strings per RFC 3986.
 */
function stringifySearchWith(obj: Record<string, unknown>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(obj)) {
    if (value === undefined || value === null || value === '') continue
    // Search values are primitives or arrays (CSV like ?identity=10101,10102);
    // arrays stringify comma-joined, matching String(array).
    params.set(
      key,
      Array.isArray(value) ? value.join(',') : String(value as string | number | boolean),
    )
  }
  const str = params.toString()
  if (!str) return ''
  // Restore commas that URLSearchParams encoded
  return '?' + str.replace(/%2C/gi, ',')
}

// Keys that should be parsed as numbers (pagination)
const NUMERIC_SEARCH_KEYS = new Set(['page'])

function parseSearchWith(searchStr: string): Record<string, unknown> {
  const params = new URLSearchParams(searchStr.startsWith('?') ? searchStr.slice(1) : searchStr)
  const result: Record<string, unknown> = {}
  for (const [key, value] of params.entries()) {
    if (NUMERIC_SEARCH_KEYS.has(key)) {
      const num = Number(value)
      result[key] = Number.isFinite(num) ? num : value
    } else {
      result[key] = value
    }
  }
  return result
}

export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: NotFoundPage,
  defaultErrorComponent: RouteErrorComponent,
  defaultPendingComponent: RoutePendingFallback,
  // Preload a route's chunk + loader on link hover/touch so the ~100ms serial
  // chunk-fetch window is paid before the click, not after it. Loaders are
  // cache-idempotent (query-cache prefetch with staleTime), so a hover that
  // never converts to a navigation only warms the cache.
  defaultPreload: 'intent',
  // Scroll to top on navigation; hash fragments (e.g., #comment-uuid) auto-scroll to element
  scrollRestoration: true,
  // Show pending component immediately on navigation (no delay)
  defaultPendingMs: 0,
  // Minimum time to show pending component (prevents flash on very fast loads)
  defaultPendingMinMs: 200,
  stringifySearch: stringifySearchWith,
  parseSearch: parseSearchWith,
})

syncTitleOnLanguageChange(router)

// Register router for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
