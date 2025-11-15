import { createRouter, createRootRoute, createRoute } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import HomePage from '@/routes/HomePage'
import AboutPage from '@/routes/AboutPage'
import InfoPage from '@/routes/InfoPage'
import IdentityPage from '@/routes/IdentityPage'
import PlannerPage from '@/routes/PlannerPage'
import CommunityPage from '@/routes/CommunityPage'
import GoogleCallback from '@/routes/auth/callback/google'
import { GlobalLayout } from '@/components/GlobalLayout'

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

// Google OAuth callback route - path: "/auth/callback/google"
const googleCallbackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/auth/callback/google',
  component: GoogleCallback,
})

// Create route tree
const routeTree = rootRoute.addChildren([
  indexRoute,
  aboutRoute,
  infoRoute,
  identityRoute,
  plannerRoute,
  communityRoute,
  googleCallbackRoute,
])

// Create and export router instance
export const router = createRouter({ routeTree })

// Register router for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
