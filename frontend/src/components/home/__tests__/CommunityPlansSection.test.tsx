/**
 * CommunityPlansSection.test.tsx
 *
 * Tests for CommunityPlansSection with ErrorBoundary integration.
 * Verifies error handling and fallback display.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { CommunityPlansSection } from '../CommunityPlansSection'

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
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.home.communityPlans.title': 'Community Plans',
          'pages.home.communityPlans.browseAll': 'Browse All',
          'pages.home.communityPlans.tabLatest': 'Latest',
          'pages.home.communityPlans.tabRecommended': 'Recommended',
          'pages.home.communityPlans.empty': 'No plans available',
          'errors.communityPlans.title': 'Connection Lost',
          'errors.communityPlans.connectionLost': '... My connection to Faust has been severed.',
          'errors.generic.retry': 'Try Again',
        }
        return translations[key] || key
      },
    }),
  }
})

// Mock PublishedPlannerCard
vi.mock('@/components/plannerList/PublishedPlannerCard', () => ({
  PublishedPlannerCard: ({ planner }: { planner: { id: string; title: string } }) => (
    <div data-testid={`planner-card-${planner.id}`}>{planner.title}</div>
  ),
}))

// Mock ResponsiveCardGrid
vi.mock('@/components/common/ResponsiveCardGrid', () => ({
  ResponsiveCardGrid: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="card-grid">{children}</div>
  ),
}))

// Mock useMDGesellschaftData hook
const mockUseMDGesellschaftData = vi.fn()
vi.mock('@/hooks/useMDGesellschaftData', () => ({
  useMDGesellschaftData: (...args: unknown[]) => mockUseMDGesellschaftData(...args),
}))

describe('CommunityPlansSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders section title and browse link', async () => {
    mockUseMDGesellschaftData.mockReturnValue({
      data: {
        content: [],
        totalPages: 1,
        totalElements: 0,
        number: 0,
        size: 20,
      },
    })

    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByText('Community Plans')).toBeInTheDocument()
      expect(screen.getByText('Browse All')).toBeInTheDocument()
    })
  })

  it('renders tab switcher', async () => {
    mockUseMDGesellschaftData.mockReturnValue({
      data: {
        content: [],
        totalPages: 1,
        totalElements: 0,
        number: 0,
        size: 20,
      },
    })

    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByText('Latest')).toBeInTheDocument()
      expect(screen.getByText('Recommended')).toBeInTheDocument()
    })
  })

  it('displays error fallback when data fetching fails', async () => {
    // Mock hook to throw error
    mockUseMDGesellschaftData.mockImplementation(() => {
      throw new Error('API Error')
    })

    const queryClient = createTestQueryClient()

    // Suppress console.error for this test (ErrorBoundary logs errors)
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    // Error boundary should catch the error and show fallback
    await waitFor(() => {
      expect(screen.getByText('Connection Lost')).toBeInTheDocument()
      expect(screen.getByText('... My connection to Faust has been severed.')).toBeInTheDocument()
    })

    consoleSpy.mockRestore()
  })

  it('displays retry button in error fallback', async () => {
    mockUseMDGesellschaftData.mockImplementation(() => {
      throw new Error('API Error')
    })

    const queryClient = createTestQueryClient()
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    await waitFor(() => {
      const retryButton = screen.getByRole('button', { name: 'Try Again' })
      expect(retryButton).toBeInTheDocument()
    })

    consoleSpy.mockRestore()
  })

  it('does not show error fallback when data loads successfully', async () => {
    mockUseMDGesellschaftData.mockReturnValue({
      data: {
        content: [
          {
            id: 'test-id-1',
            title: 'Test Planner 1',
            category: '5F',
            selectedKeywords: [],
            upvotes: 10,
            viewCount: 100,
            authorUsernameEpithet: 'TestUser',
            authorUsernameSuffix: '001',
            lastModifiedAt: '2024-12-31T12:00:00Z',
            isBookmarked: false,
          },
        ],
        totalPages: 1,
        totalElements: 1,
        number: 0,
        size: 20,
      },
    })

    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    await waitFor(() => {
      // Should show planner card, not error
      expect(screen.getByTestId('planner-card-test-id-1')).toBeInTheDocument()
      expect(screen.queryByText('Connection Lost')).not.toBeInTheDocument()
    })
  })

  it('renders planner cards when data is available', async () => {
    mockUseMDGesellschaftData.mockReturnValue({
      data: {
        content: [
          {
            id: 'test-id-1',
            title: 'Test Planner 1',
            category: '5F',
            selectedKeywords: [],
            upvotes: 10,
            viewCount: 100,
            authorUsernameEpithet: 'TestUser',
            authorUsernameSuffix: '001',
            lastModifiedAt: '2024-12-31T12:00:00Z',
            isBookmarked: false,
          },
          {
            id: 'test-id-2',
            title: 'Test Planner 2',
            category: '10F',
            selectedKeywords: [],
            upvotes: 20,
            viewCount: 200,
            authorUsernameEpithet: 'AnotherUser',
            authorUsernameSuffix: '002',
            lastModifiedAt: '2024-12-31T13:00:00Z',
            isBookmarked: false,
          },
        ],
        totalPages: 1,
        totalElements: 2,
        number: 0,
        size: 20,
      },
    })

    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('planner-card-test-id-1')).toBeInTheDocument()
      expect(screen.getByTestId('planner-card-test-id-2')).toBeInTheDocument()
      expect(screen.getByText('Test Planner 1')).toBeInTheDocument()
      expect(screen.getByText('Test Planner 2')).toBeInTheDocument()
    })
  })

  it('shows empty state when no planners are available', async () => {
    mockUseMDGesellschaftData.mockReturnValue({
      data: {
        content: [],
        totalPages: 0,
        totalElements: 0,
        number: 0,
        size: 20,
      },
    })

    const queryClient = createTestQueryClient()

    render(
      <QueryClientProvider client={queryClient}>
        <CommunityPlansSection />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(screen.getByText('No plans available')).toBeInTheDocument()
    })
  })
})
