import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { BanStatusBanner } from './BanStatusBanner'
import type { User } from '@/schemas/AuthSchemas'

// Mock useAuthQuery
const mockUseAuthQuery = vi.fn()
vi.mock('@/hooks/useAuthQuery', () => ({
  useAuthQuery: () => mockUseAuthQuery(),
}))

// Mock react-i18next
vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string, params?: Record<string, string>) => {
      if (key === 'moderation.accountSuspended' && params?.reason) {
        return `Your account has been suspended. Reason: ${params.reason}`
      }
      if (key === 'moderation.accountSuspendedNoReason') {
        return 'Your account has been suspended'
      }
      if (key === 'moderation.contactSupport') {
        return 'Contact contact@dante-planner.com for assistance'
      }
      return key
    },
  }),
}))

describe('BanStatusBanner', () => {
  const queryClient = createTestQueryClient()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders banner when user is banned with reason', () => {
    const bannedUser: User = {
      email: 'test@example.com',
      usernameEpithet: 'TEST',
      usernameSuffix: 'test1',
      isBanned: true,
      bannedAt: new Date().toISOString(),
      banReason: 'Violated terms of service',
    }

    mockUseAuthQuery.mockReturnValue({ data: bannedUser })

    render(
      <QueryClientProvider client={queryClient}>
        <BanStatusBanner />
      </QueryClientProvider>
    )

    expect(screen.getByText(/Your account has been suspended. Reason: Violated terms of service/)).toBeDefined()
    expect(screen.getByText(/Contact contact@dante-planner.com for assistance/)).toBeDefined()
  })

  it('renders banner when user is banned without reason', () => {
    const bannedUser: User = {
      email: 'test@example.com',
      usernameEpithet: 'TEST',
      usernameSuffix: 'test1',
      isBanned: true,
      bannedAt: new Date().toISOString(),
    }

    mockUseAuthQuery.mockReturnValue({ data: bannedUser })

    render(
      <QueryClientProvider client={queryClient}>
        <BanStatusBanner />
      </QueryClientProvider>
    )

    expect(screen.getByText(/Your account has been suspended/)).toBeDefined()
  })

  it('renders banner when user is timed out', () => {
    const timedOutUser: User = {
      email: 'test@example.com',
      usernameEpithet: 'TEST',
      usernameSuffix: 'test1',
      isTimedOut: true,
      timeoutUntil: new Date(Date.now() + 3600000).toISOString(),
      timeoutReason: 'Spam detected',
    }

    mockUseAuthQuery.mockReturnValue({ data: timedOutUser })

    render(
      <QueryClientProvider client={queryClient}>
        <BanStatusBanner />
      </QueryClientProvider>
    )

    expect(screen.getByText(/Your account has been suspended. Reason: Spam detected/)).toBeDefined()
  })

  it('does not render when user is not restricted', () => {
    const normalUser: User = {
      email: 'test@example.com',
      usernameEpithet: 'TEST',
      usernameSuffix: 'test1',
    }

    mockUseAuthQuery.mockReturnValue({ data: normalUser })

    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <BanStatusBanner />
      </QueryClientProvider>
    )

    // Banner should not render (container is empty)
    expect(container.firstChild).toBeNull()
  })

  it('dismisses banner when close button clicked', async () => {
    const user = userEvent.setup()

    const bannedUser: User = {
      email: 'test@example.com',
      usernameEpithet: 'TEST',
      usernameSuffix: 'test1',
      isBanned: true,
      bannedAt: new Date().toISOString(),
      banReason: 'Test',
    }

    mockUseAuthQuery.mockReturnValue({ data: bannedUser })

    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <BanStatusBanner />
      </QueryClientProvider>
    )

    // Banner is visible
    expect(screen.getByText(/Your account has been suspended/)).toBeDefined()

    // Click close button
    const closeButton = container.querySelector('button')
    expect(closeButton).toBeDefined()
    await user.click(closeButton!)

    // Banner should be hidden
    expect(container.firstChild).toBeNull()
  })

  it('does not render when user is null (guest)', () => {
    mockUseAuthQuery.mockReturnValue({ data: null })

    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <BanStatusBanner />
      </QueryClientProvider>
    )

    expect(container.firstChild).toBeNull()
  })
})
