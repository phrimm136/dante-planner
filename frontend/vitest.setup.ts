import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// The plugin project runs in the node environment, where none of the browser
// globals below exist and there is nothing to unmount.
const hasDom = typeof window !== 'undefined'

// Cleanup after each test
afterEach(() => {
  if (hasDom) cleanup()
})

// Mock window.matchMedia (required by UI libraries like shadcn/ui).
// Width queries are answered from window.innerWidth so breakpoint hooks report
// the same viewport the layout code sees; anything else stays unmatched.
function matchesWidthQuery(query: string): boolean {
  const min = query.match(/\(min-width:\s*(\d+(?:\.\d+)?)px\)/)
  if (min) return window.innerWidth >= Number(min[1])
  const max = query.match(/\(max-width:\s*(\d+(?:\.\d+)?)px\)/)
  if (max) return window.innerWidth <= Number(max[1])
  return false
}

if (hasDom) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn<(query: string) => MediaQueryList>().mockImplementation(
      (query: string) =>
        ({
          matches: matchesWidthQuery(query),
          media: query,
          onchange: null,
          addListener: vi.fn<() => void>(),
          removeListener: vi.fn<() => void>(),
          addEventListener: vi.fn<() => void>(),
          removeEventListener: vi.fn<() => void>(),
          dispatchEvent: vi.fn<() => boolean>(),
        }) as unknown as MediaQueryList,
    ),
  })

  // Mock IntersectionObserver (used by lazy loading components)
  globalThis.IntersectionObserver = class IntersectionObserver {
    constructor() {}
    disconnect() {}
    observe() {}
    takeRecords() {
      return []
    }
    unobserve() {}
  } as any

  // Mock ResizeObserver (used by Radix UI components)
  globalThis.ResizeObserver = class ResizeObserver {
    constructor() {}
    disconnect() {}
    observe() {}
    unobserve() {}
  } as any

  // Mock scrollIntoView (used by cmdk Command component)
  Element.prototype.scrollIntoView = vi.fn<() => void>()
}

// Mock env.ts to prevent Zod validation from requiring real env vars
vi.mock('@/lib/env', () => ({
  env: {
    VITE_GOOGLE_CLIENT_ID: 'test-client-id',
    VITE_API_BASE_URL: 'http://localhost:8080',
    DEV: false,
    PROD: false,
    MODE: 'test',
  },
}))

// Mock fetch for testing (simple implementation)
globalThis.fetch = vi.fn<() => Promise<Response>>() as any
