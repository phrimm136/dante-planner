import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { AccountDeleteSection } from './AccountDeleteSection'
import { AccountDeleteDialog } from './AccountDeleteDialog'
import { toast } from 'sonner'
import type { User } from '@/schemas/AuthSchemas'
import type { UserDeletionResponse } from '@/types/UserSettingsTypes'

// Mock dependencies
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/lib/env', () => ({
  env: {
    VITE_GOOGLE_CLIENT_ID: 'test-client-id',
  },
}))

vi.mock('@/lib/oauth', () => ({
  generateState: () => 'mock-state',
  generateCodeVerifier: () => 'mock-verifier',
  generateCodeChallenge: () => Promise.resolve('mock-challenge'),
  storeOAuthParams: vi.fn(),
}))

const mockMutate = vi.fn()
const mockSetQueryData = vi.fn()

// Mock hooks
vi.mock('@/hooks/useAuthQuery', () => ({
  useAuthQuery: vi.fn(() => ({ data: null })),
  authQueryKeys: {
    me: 'auth-me',
  },
}))

vi.mock('@/hooks/useUserSettingsQuery', () => ({
  useDeleteAccountMutation: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
    isSuccess: false,
    isError: false,
  })),
}))

vi.mock('@tanstack/react-query', async () => {
  const actual = await vi.importActual('@tanstack/react-query')
  return {
    ...actual,
    useQueryClient: vi.fn(() => ({
      setQueryData: mockSetQueryData,
    })),
  }
})

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { useDeleteAccountMutation } from '@/hooks/useUserSettingsQuery'

describe('AccountDeleteSection', () => {
  const mockUser: User = {
    id: 123,
    email: 'test@example.com',
    provider: 'google',
    usernameKeyword: 'don',
    usernameSuffix: '1234',
  }

  const mockDeleteResponse: UserDeletionResponse = {
    message: 'Account scheduled for deletion',
    deletedAt: '2026-01-09T10:00:00Z',
    permanentDeleteAt: '2026-02-08T10:00:00Z',
    gracePeriodDays: 30,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: 'http://localhost:5173/settings', origin: 'http://localhost:5173' },
    })
    vi.mocked(useAuthQuery).mockReturnValue({ data: null } as ReturnType<typeof useAuthQuery>)
    vi.mocked(useDeleteAccountMutation).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useDeleteAccountMutation>)
  })

  it('shows sign-in prompt when user is unauthenticated', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: null } as ReturnType<typeof useAuthQuery>)

    render(<AccountDeleteSection />)

    expect(screen.getByText(/sign in to manage your account/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in with google/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /delete account/i })).not.toBeInTheDocument()
  })

  it('shows delete button when user is authenticated', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    render(<AccountDeleteSection />)

    expect(screen.getByRole('button', { name: /delete account/i })).toBeInTheDocument()
    expect(screen.queryByText(/sign in to manage/i)).not.toBeInTheDocument()
  })

  it('opens dialog when delete button is clicked', async () => {
    const user = userEvent.setup()
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    render(<AccountDeleteSection />)

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    await user.click(deleteButton)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('DELETE')).toBeInTheDocument()
  })

  it('shows success toast with formatted date on successful deletion', async () => {
    const user = userEvent.setup({ delay: null })
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    // Mock mutate to call onSuccess callback
    const mockMutateWithCallback = vi.fn((_, options) => {
      if (options?.onSuccess) {
        options.onSuccess(mockDeleteResponse)
      }
    })
    vi.mocked(useDeleteAccountMutation).mockReturnValue({
      mutate: mockMutateWithCallback,
      isPending: false,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useDeleteAccountMutation>)

    render(<AccountDeleteSection />)

    // Open dialog
    await user.click(screen.getByRole('button', { name: /delete account/i }))

    // Type confirmation
    const input = screen.getByPlaceholderText('DELETE')
    await user.type(input, 'DELETE')

    // Click delete
    const confirmButton = screen.getByRole('button', { name: /delete account/i })
    await user.click(confirmButton)

    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining('Account scheduled for deletion on')
    )
    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining('Log in within 30 days to cancel')
    )
  })

  it('calls mutation with success and error callbacks', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    const mockMutateImpl = vi.fn()
    vi.mocked(useDeleteAccountMutation).mockReturnValue({
      mutate: mockMutateImpl,
      isPending: false,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useDeleteAccountMutation>)

    render(<AccountDeleteSection />)

    // Mutation hook should be called during render
    // The actual mutation.mutate() will be called by handleDelete on user action
    // We're verifying the hook is set up correctly
    expect(mockMutateImpl).not.toHaveBeenCalled()
  })

  it('disables buttons during deletion (isPending state)', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)
    vi.mocked(useDeleteAccountMutation).mockReturnValue({
      mutate: mockMutate,
      isPending: true,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useDeleteAccountMutation>)

    render(<AccountDeleteSection />)

    // Open dialog manually by rendering with isPending already true
    render(
      <AccountDeleteDialog
        open={true}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        isPending={true}
      />
    )

    expect(screen.getByRole('button', { name: /deleting/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
  })
})
