import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// Cleanup after each test
afterEach(() => {
  cleanup()
})

// Mock window.matchMedia (required by UI libraries like shadcn/ui)
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
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
Element.prototype.scrollIntoView = vi.fn()

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
globalThis.fetch = vi.fn() as any
