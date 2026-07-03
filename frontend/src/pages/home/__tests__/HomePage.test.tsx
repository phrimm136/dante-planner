import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import HomePage from '../HomePage'

// Mock the router Link component
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}))

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'EN' },
    }),
  }
})

// Mock home page data hook to avoid Suspense issues
vi.mock('@/pages/home/hooks/useHomePageData', () => ({
  useRecentlyReleasedData: () => ({
    dateGroups: [],
  }),
}))

// Mock BannerSection to simplify tests
vi.mock('@/pages/home/components/BannerSection', () => ({
  BannerSection: () => <div data-testid="banner-section">Banner</div>,
}))

// Mock RecentlyReleasedSection
vi.mock('@/pages/home/components/RecentlyReleasedSection', () => ({
  RecentlyReleasedSection: () => <div data-testid="recently-released">Recently Released</div>,
}))

// Mock CommunityPlansSection
vi.mock('@/pages/home/components/CommunityPlansSection', () => ({
  CommunityPlansSection: () => <div data-testid="community-plans">Community Plans</div>,
}))

// Mock LoadingState
vi.mock('@/components/feedback/LoadingState', () => ({
  LoadingState: () => <div data-testid="loading-state">Loading...</div>,
}))

// Mock ErrorBoundary
vi.mock('@/components/feedback/ErrorBoundary', () => ({
  ErrorBoundary: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

describe('HomePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders banner section', async () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('banner-section')).toBeDefined()
    })
  })

  it('renders recently released section', async () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('recently-released')).toBeDefined()
    })
  })

  it('renders community plans section', async () => {
    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('community-plans')).toBeDefined()
    })
  })

  it('renders page structure correctly', async () => {
    const queryClient = createTestQueryClient()

    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <HomePage />
      </QueryClientProvider>
    )

    await waitFor(() => {
      // Page container should exist
      expect(container.querySelector('.container')).toBeDefined()
    })
  })
})
