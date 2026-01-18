/**
 * UsernameSection.test.tsx
 *
 * Tests for username section component rendering and behavior.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { User } from '@/schemas/AuthSchemas'

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, defaultValue?: string | object) => {
        const translations: Record<string, string> = {
          'settings.username.title': 'Username',
          'settings.username.signInPrompt': 'Sign in to customize your username',
          'header.auth.googleLogin': 'Sign in with Google',
          'settings.username.current': 'Current',
          'settings.username.save': 'Save',
          'settings.username.saving': 'Saving...',
          'settings.username.preview': 'Preview',
          'association.sinner': 'Sinner',
        }
        if (typeof defaultValue === 'string') {
          return translations[key] ?? defaultValue
        }
        return translations[key] ?? key
      },
    }),
  }
})

// Mock oauth functions
vi.mock('@/lib/oauth', () => ({
  generateState: () => 'mock-state',
  generateCodeVerifier: () => 'mock-verifier',
  generateCodeChallenge: () => Promise.resolve('mock-challenge'),
  storeOAuthParams: vi.fn(),
}))

// Mock env
vi.mock('@/lib/env', () => ({
  env: {
    VITE_GOOGLE_CLIENT_ID: 'mock-client-id',
  },
}))

// Mock sonner toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

// Mock data
const mockAssociations = [
  { keyword: 'LCB', displayName: 'LCB' },
  { keyword: 'W_CORP', displayName: 'W_CORP' },
  { keyword: 'ZWEI', displayName: 'ZWEI' },
]

const mockUser: User = {
  id: 1,
  email: 'test@example.com',
  provider: 'google',
  usernameKeyword: 'LCB',
  usernameSuffix: '1234',
}

const mockMutate = vi.fn()

// Mock hooks
vi.mock('@/hooks/useAuthQuery', () => ({
  useAuthQuery: vi.fn(() => ({ data: null })),
}))

vi.mock('@/hooks/useUserSettingsQuery', () => ({
  useAssociationsQuery: vi.fn(() => ({
    associations: mockAssociations,
  })),
  useUpdateKeywordMutation: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}))

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { useAssociationsQuery, useUpdateKeywordMutation } from '@/hooks/useUserSettingsQuery'
import { UsernameSection } from './UsernameSection'

describe('UsernameSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAuthQuery).mockReturnValue({ data: null } as ReturnType<typeof useAuthQuery>)
    vi.mocked(useAssociationsQuery).mockReturnValue({ associations: mockAssociations })
    vi.mocked(useUpdateKeywordMutation).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useUpdateKeywordMutation>)
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
      const wCorpOption = screen.getByRole('menuitemradio', { name: /w_corp/i })
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
      vi.mocked(useUpdateKeywordMutation).mockReturnValue({
        mutate: mockMutate,
        isPending: true,
      } as unknown as ReturnType<typeof useUpdateKeywordMutation>)

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

      const wCorpOption = screen.getByRole('menuitemradio', { name: /w_corp/i })
      await user.click(wCorpOption)

      const saveButton = screen.getByRole('button', { name: /save/i })
      await user.click(saveButton)

      expect(mockMutate).toHaveBeenCalledWith(
        { keyword: 'W_CORP' },
        expect.objectContaining({
          onSuccess: expect.any(Function),
          onError: expect.any(Function),
        })
      )
    })
  })
})
