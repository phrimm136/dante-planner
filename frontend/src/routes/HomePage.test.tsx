import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import HomePage from './HomePage'

// Mock the router Link component
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}))

describe('HomePage', () => {
  beforeEach(() => {
    // Mock fetch to return test data
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        message: 'Hello from mocked fetch!',
        timestamp: Date.now(),
      }),
    } as Response)
  })

  it('renders page title', () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    expect(screen.getByText(/limbus planner/i)).toBeDefined()
  })

  it('displays loading state initially', () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    expect(screen.getByText(/loading data/i)).toBeDefined()
  })

  it('fetches and displays query data', async () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    // Wait for loading to finish and data to appear
    await waitFor(
      () => {
        expect(screen.getByText(/hello from mocked fetch/i)).toBeDefined()
      },
      { timeout: 2000 }
    )
  })

  it('shows navigation link to about page', () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    const aboutLink = screen.getByRole('link', { name: /go to about/i })
    expect(aboutLink).toBeDefined()
    expect(aboutLink.getAttribute('href')).toBe('/about')
  })
})
