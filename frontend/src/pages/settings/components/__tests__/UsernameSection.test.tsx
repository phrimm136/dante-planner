/**
 * UsernameSection.test.tsx
 *
 * Tests for username section component rendering and behavior.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { UseMutateFunction } from '@tanstack/react-query'
import { buildMutationResult } from '@/test-utils'
import type { User } from '@/shared/auth'
import type { UpdateUsernameEpithetRequest } from '../../types/UserSettingsTypes'

/** `mutate` as the component sees it: the epithet-update mutation's own signature. */
type UpdateEpithetMutate = UseMutateFunction<User, Error, UpdateUsernameEpithetRequest, unknown>

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      i18n: { language: 'en' },
      t: (key: string, options?: string | { ns?: string; defaultValue?: string }) => {
        const translations: Record<string, string> = {
          'settings.username.title': 'Username',
          'settings.username.signInPrompt': 'Sign in to customize your username',
          'header.auth.googleLogin': 'Sign in with Google',
          'settings.username.current': 'Current',
          'settings.username.save': 'Save',
          'settings.username.saving': 'Saving...',
          'settings.username.preview': 'Preview',
          'epithet.sinner': 'Sinner',
          LCB: 'LCB',
          W_CORP: 'W Corp',
          ZWEI: 'ZWEI',
        }
        if (typeof options === 'string') {
          return translations[key] ?? options
        }
        if (typeof options === 'object' && options?.defaultValue) {
          return translations[key] ?? options.defaultValue
        }
        return translations[key] ?? key
      },
    }),
  }
})

// Mock sonner toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

// Mock data
const mockEpithets = ['LCB', 'W_CORP', 'ZWEI']

const mockUser: User = {
  email: 'test@example.com',
  usernameEpithet: 'LCB',
  usernameSuffix: '1234',
  role: 'NORMAL',
}

const mockMutate = vi.fn<UpdateEpithetMutate>()

// Mock hooks
vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  useAuthQuery: vi.fn(() => ({ data: null })),
}))

vi.mock('../../hooks/useUserSettingsQuery', () => ({
  useEpithetsQuery: vi.fn(() => ({
    epithets: mockEpithets,
  })),
  useUpdateEpithetMutation: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}))

import { useAuthQuery } from '@/shared/auth'
import { useEpithetsQuery, useUpdateEpithetMutation } from '../../hooks/useUserSettingsQuery'
import { UsernameSection } from '../UsernameSection'

describe('UsernameSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAuthQuery).mockReturnValue({ data: null } as ReturnType<typeof useAuthQuery>)
    vi.mocked(useEpithetsQuery).mockReturnValue({ epithets: mockEpithets })
    vi.mocked(useUpdateEpithetMutation).mockReturnValue(
      buildMutationResult<User, Error, UpdateUsernameEpithetRequest, unknown>({
        mutate: mockMutate,
        isPending: false,
      }),
    )
  })

  describe('unauthenticated state', () => {
    it('shows sign-in prompt when user is not authenticated', () => {
      render(<UsernameSection />)
      expect(screen.getByText('Sign in to customize your username')).toBeInTheDocument()
    })

    it('shows Google login button when user is not authenticated', () => {
      render(<UsernameSection />)
      const loginButton = screen.getByRole('button', { name: /sign in with google/i })
      expect(loginButton).toBeInTheDocument()
    })

    it('shows username title in unauthenticated state', () => {
      render(<UsernameSection />)
      expect(screen.getByText('Username')).toBeInTheDocument()
    })
  })

  describe('authenticated state', () => {
    beforeEach(() => {
      vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)
    })

    it('shows dropdown trigger with current keyword display name', () => {
      render(<UsernameSection />)
      const dropdownTrigger = screen.getByRole('button', { name: /lcb/i })
      expect(dropdownTrigger).toBeInTheDocument()
    })

    it('shows current username preview', () => {
      render(<UsernameSection />)
      expect(screen.getByText(/current/i)).toBeInTheDocument()
    })

    it('shows save button', () => {
      render(<UsernameSection />)
      const saveButton = screen.getByRole('button', { name: /save/i })
      expect(saveButton).toBeInTheDocument()
    })

    it('save button is disabled by default when no selection change', () => {
      render(<UsernameSection />)
      const saveButton = screen.getByRole('button', { name: /save/i })
      expect(saveButton).toBeDisabled()
    })

    it('does not show Google login button when authenticated', () => {
      render(<UsernameSection />)
      expect(screen.queryByRole('button', { name: /sign in with google/i })).not.toBeInTheDocument()
    })
  })

  describe('save button state', () => {
    beforeEach(() => {
      vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)
    })

    it('save button is enabled when keyword differs from current', async () => {
      const user = userEvent.setup()
      render(<UsernameSection />)

      // Open dropdown
      const dropdownTrigger = screen.getByRole('button', { name: /lcb/i })
      await user.click(dropdownTrigger)

      // Select a different keyword
      const wCorpOption = screen.getByRole('menuitemradio', { name: /w corp/i })
      await user.click(wCorpOption)

      // Save button should now be enabled
      const saveButton = screen.getByRole('button', { name: /save/i })
      expect(saveButton).toBeEnabled()
    })

    it('shows preview when keyword differs from current', async () => {
      const user = userEvent.setup()
      render(<UsernameSection />)

      const dropdownTrigger = screen.getByRole('button', { name: /lcb/i })
      await user.click(dropdownTrigger)

      const zweiOption = screen.getByRole('menuitemradio', { name: /ZWEI/i })
      await user.click(zweiOption)

      expect(screen.getByText(/preview/i)).toBeInTheDocument()
    })

    it('save button is disabled while mutation is pending', () => {
      vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)
      vi.mocked(useUpdateEpithetMutation).mockReturnValue(
        buildMutationResult<User, Error, UpdateUsernameEpithetRequest, unknown>({
          mutate: mockMutate,
          isPending: true,
        }),
      )

      render(<UsernameSection />)

      const saveButton = screen.getByRole('button', { name: /saving/i })
      expect(saveButton).toBeDisabled()
    })
  })

  describe('save interaction', () => {
    beforeEach(() => {
      vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)
    })

    it('calls mutation when save button is clicked', async () => {
      const user = userEvent.setup()
      render(<UsernameSection />)

      const dropdownTrigger = screen.getByRole('button', { name: /lcb/i })
      await user.click(dropdownTrigger)

      const wCorpOption = screen.getByRole('menuitemradio', { name: /w corp/i })
      await user.click(wCorpOption)

      const saveButton = screen.getByRole('button', { name: /save/i })
      await user.click(saveButton)

      // RFC 0004 stream 4(d) line 526 deletes this component's error toast, so
      // it no longer supplies an onError; the mutation cache reports failures.
      expect(mockMutate).toHaveBeenCalledWith(
        { epithet: 'W_CORP' },
        expect.objectContaining({ onSuccess: expect.any(Function) }),
      )
    })
  })
})
