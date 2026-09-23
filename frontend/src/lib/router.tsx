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

import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { IdentityDetailSkeleton } from '@/pages/identity/components/IdentityDetailSkeleton'
import { EGO_GIFT_GEOMETRY, IDENTITY_GEOMETRY, THEME_PACK_GEOMETRY } from '@/shared/cardLayout'
import { EGODetailSkeleton } from '@/pages/ego/components/EGODetailSkeleton'
import { EGOListSkeleton } from '@/pages/ego/components/EGOListSkeleton'
import { EGOGiftDetailSkeleton } from '@/pages/egoGift/components/EGOGiftDetailSkeleton'
import { ThemePackDetailSkeleton } from '@/pages/themePack/components/ThemePackDetailSkeleton'
import { AbEventDetailSkeleton } from '@/pages/abEvent/components/AbEventDetailSkeleton'
import { AB_EVENT_GEOMETRY } from '@/pages/abEvent/lib/cardLayout'
import { KeywordDetailSkeleton } from '@/pages/keyword/components/KeywordDetailSkeleton'
import { KEYWORD_GEOMETRY } from '@/pages/keyword/lib/cardLayout'
import {
  DeckBuilderPageSkeleton,
  PlannerMDNewPageSkeleton,
  PlannerMDPageSkeleton,
  PlannerViewerSkeleton,
} from '@/pages/planner/components/plannerSkeletons'
import { SettingsPageSkeleton } from '@/pages/settings/components/SettingsPageSkeleton'
import { SECTION_STYLES } from '@/lib/constants'

import NotFoundPage from '@/components/feedback/NotFoundPage'

const pageTitle = (key: string, ns = 'common') => `${i18n.t(key, { ns })} | Dante's Planner`

const detailHead = (title: string | undefined, fallback: string) => ({
  meta: [{ title: `${title ?? fallback} | Dante's Planner` }],
})

const mdUserDefaults = {
  page: 0,
}

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

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: lazyRouteComponent(() => import('@/pages/home/HomePage')),
  head: () => ({
    meta: [{ title: "Dante's Planner - Limbus Company Database and Planning Tool" }],
  }),
})

const plannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.planner.title') }],
  }),
})

const plannerMDRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDPage')),
  pendingComponent: PlannerMDPageSkeleton,
  validateSearch: zodValidator(mdUserSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdUserDefaults)],
  },
  head: () => ({
    meta: [{ title: pageTitle('header.nav.mirrorDungeon') }],
  }),
})

const plannerMDGesellschaftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/gesellschaft',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDGesellschaftPage')),
  pendingComponent: PlannerMDPageSkeleton,
  validateSearch: zodValidator(mdGesellschaftSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdGesellschaftDefaults)],
  },
  head: () => ({
    meta: [{ title: pageTitle('pages.home.communityPlans.title') }],
  }),
})

const plannerMDGesellschaftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/gesellschaft/$id',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDGesellschaftDetailPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <PlannerViewerSkeleton />
    </div>
  ),
  validateSearch: zodValidator(mdGesellschaftSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdGesellschaftDefaults)],
  },
  loader: loadPublishedPlanner,
  head: ({ loaderData }) => detailHead(loaderData?.title, untitledPlannerTitle()),
})

const plannerMDNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/new',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDNewPage')),
  pendingComponent: PlannerMDNewPageSkeleton,
  head: () => ({
    meta: [{ title: pageTitle('pages.plannerMD.newPlan', 'planner') }],
  }),
})

const deckBuilderRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/deck',
  component: lazyRouteComponent(() => import('@/pages/planner/DeckBuilderPage')),
  pendingComponent: DeckBuilderPageSkeleton,
  head: () => ({
    meta: [{ title: pageTitle('header.nav.deckBuilder') }],
  }),
})

const plannerMDDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDDetailPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <PlannerViewerSkeleton />
    </div>
  ),
  validateSearch: zodValidator(mdUserSearchSchema),
  search: {
    middlewares: [stripSearchParams(mdUserDefaults)],
  },
  loader: loadPlannerTitleRoute,
  head: ({ loaderData }) => detailHead(loaderData?.title, untitledPlannerTitle()),
})

const plannerMDEditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/$id/edit',
  component: lazyRouteComponent(() => import('@/pages/planner/PlannerMDEditPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <PlannerViewerSkeleton />
    </div>
  ),
  loader: loadPlannerTitleRoute,
  head: ({ loaderData }) => ({
    meta: [
      {
        title: `${i18n.t('pages.edit.title', { ns: 'planner' })} - ${loaderData?.title ?? untitledPlannerTitle()} | Dante's Planner`,
      },
    ],
  }),
})

const extractionPlannerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/extraction',
  component: lazyRouteComponent(() => import('@/pages/extraction/ExtractionPlannerPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.extraction') }],
  }),
})

const identityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity',
  component: lazyRouteComponent(() => import('@/pages/identity/IdentityPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <ListPageSkeleton geometry={IDENTITY_GEOMETRY} />
    </div>
  ),

  head: () => ({
    meta: [{ title: pageTitle('header.nav.identity') }],
  }),
})

const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: lazyRouteComponent(() => import('@/pages/identity/IdentityDetailPage')),
  pendingComponent: IdentityDetailSkeleton,

  loader: loadIdentityName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'Identity'),
})

const egoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego',
  component: lazyRouteComponent(() => import('@/pages/ego/EGOPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <EGOListSkeleton />
    </div>
  ),

  head: () => ({
    meta: [{ title: pageTitle('header.nav.ego') }],
  }),
})

const egoDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego/$id',
  component: lazyRouteComponent(() => import('@/pages/ego/EGODetailPage')),
  pendingComponent: EGODetailSkeleton,

  loader: loadEgoName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'EGO'),
})

const egoGiftRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift',
  component: lazyRouteComponent(() => import('@/pages/egoGift/EGOGiftPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <ListPageSkeleton geometry={EGO_GIFT_GEOMETRY} />
    </div>
  ),

  head: () => ({
    meta: [{ title: pageTitle('header.nav.egoGift') }],
  }),
})

const egoGiftDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ego-gift/$id',
  component: lazyRouteComponent(() => import('@/pages/egoGift/EGOGiftDetailPage')),
  pendingComponent: EGOGiftDetailSkeleton,

  loader: loadEgoGiftName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'EGO Gift'),
})

const themePackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/theme-pack',
  component: lazyRouteComponent(() => import('@/pages/themePack/ThemePackPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <ListPageSkeleton geometry={THEME_PACK_GEOMETRY} />
    </div>
  ),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.themePack') }],
  }),
})

const themePackDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/theme-pack/$id',
  component: lazyRouteComponent(() => import('@/pages/themePack/ThemePackDetailPage')),
  pendingComponent: ThemePackDetailSkeleton,
  loader: loadThemePackName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'Theme Pack'),
})

const abEventRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ab-event',
  component: lazyRouteComponent(() => import('@/pages/abEvent/AbEventPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <ListPageSkeleton geometry={AB_EVENT_GEOMETRY} />
    </div>
  ),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.abEvent') }],
  }),
})

const abEventDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/ab-event/$id',
  component: lazyRouteComponent(() => import('@/pages/abEvent/AbEventDetailPage')),
  pendingComponent: AbEventDetailSkeleton,
  loader: loadAbEventTitle,
  head: ({ loaderData }) => detailHead(loaderData?.title, 'Dungeon Event'),
})

const keywordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/keyword',
  component: lazyRouteComponent(() => import('@/pages/keyword/KeywordPage')),
  pendingComponent: () => (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <ListPageSkeleton geometry={KEYWORD_GEOMETRY} filterCount={4} />
    </div>
  ),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.keyword') }],
  }),
})

const keywordDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/keyword/$id',
  component: lazyRouteComponent(() => import('@/pages/keyword/KeywordDetailPage')),
  pendingComponent: KeywordDetailSkeleton,
  loader: loadKeywordName,
  head: ({ loaderData }) => detailHead(loaderData?.name, 'Keyword'),
})

const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: lazyRouteComponent(() => import('@/pages/settings/SettingsPage')),
  pendingComponent: SettingsPageSkeleton,
  head: () => ({
    meta: [{ title: pageTitle('header.settings.settings') }],
  }),
})

const moderationRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/moderation',
  component: lazyRouteComponent(() => import('@/pages/moderator/ModeratorPage')),
  head: () => ({
    meta: [{ title: pageTitle('header.nav.moderator') }],
  }),
})

const privacyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/privacy',
  component: lazyRouteComponent(() => import('@/pages/legal/PrivacyPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.privacy.title') }],
  }),
})

const termsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/terms',
  component: lazyRouteComponent(() => import('@/pages/legal/TermsPage')),
  head: () => ({
    meta: [{ title: pageTitle('pages.terms.title') }],
  }),
})

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
  return '?' + str.replace(/%2C/gi, ',')
}

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
  scrollRestoration: true,
  defaultPendingMs: 0,
  defaultPendingMinMs: 200,
  stringifySearch: stringifySearchWith,
  parseSearch: parseSearchWith,
})

syncTitleOnLanguageChange(router)

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
