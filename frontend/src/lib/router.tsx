import { createRouter, createRootRoute, createRoute } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/router-devtools'
import HomePage from '@/routes/HomePage'
import AboutPage from '@/routes/AboutPage'

// Root route - contains layout for all routes
const rootRoute = createRootRoute({
  component: () => (
    <div className="min-h-screen bg-background text-foreground">
      <Outlet />
      {/* Router dev tools - only in development */}
      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
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
