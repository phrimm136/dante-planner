/**
 * CommunityPlansErrorFallback.test.tsx
 *
 * Tests for CommunityPlansErrorFallback component.
 * Verifies error message display and retry functionality.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { CommunityPlansErrorFallback } from '../CommunityPlansErrorFallback'

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        // Return specific strings for our error messages
        const translations: Record<string, string> = {
          'errors.communityPlans.title': 'Connection Lost',
          'errors.communityPlans.connectionLost': '... My connection to Faust has been severed.',
          'errors.generic.retry': 'Try Again',
        }
        return translations[key] || key
      },
    }),
  }
})

describe('CommunityPlansErrorFallback', () => {
  const mockResetErrorBoundary = vi.fn()
  const mockError = new Error('Test error')

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders error title', () => {
    render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    expect(screen.getByText('Connection Lost')).toBeInTheDocument()
  })

  it('renders Faust connection error message', () => {
    render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    expect(screen.getByText('... My connection to Faust has been severed.')).toBeInTheDocument()
  })

  it('renders retry button', () => {
    render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    const retryButton = screen.getByRole('button', { name: 'Try Again' })
    expect(retryButton).toBeInTheDocument()
  })

  it('calls resetErrorBoundary when retry button is clicked', async () => {
    const user = userEvent.setup()

    render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    const retryButton = screen.getByRole('button', { name: 'Try Again' })
    await user.click(retryButton)

    expect(mockResetErrorBoundary).toHaveBeenCalledTimes(1)
  })

  it('applies destructive styling to container', () => {
    const { container } = render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    const errorContainer = container.firstChild as HTMLElement
    expect(errorContainer).toHaveClass('bg-destructive/10')
    expect(errorContainer).toHaveClass('border-destructive')
  })

  it('applies destructive variant to retry button', () => {
    render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    const retryButton = screen.getByRole('button', { name: 'Try Again' })
    // shadcn button with variant="destructive" will have specific classes
    expect(retryButton.className).toContain('destructive')
  })

  it('renders with correct structure', () => {
    const { container } = render(
      <CommunityPlansErrorFallback
        error={mockError}
        resetErrorBoundary={mockResetErrorBoundary}
      />
    )

    // Should have title (h3), message (p), and button
    const title = container.querySelector('h3')
    const message = container.querySelector('p')
    const button = container.querySelector('button')

    expect(title).toBeInTheDocument()
    expect(message).toBeInTheDocument()
    expect(button).toBeInTheDocument()
  })
})
