import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { toast } from 'sonner'
import { QueryClient } from '@tanstack/react-query'
import type { MutationFunctionContext, UseMutateFunction } from '@tanstack/react-query'
import { buildMutationResult } from '@/test-utils'

import { LogoutEverywhereSection } from '../LogoutEverywhereSection'

/** `mutate` as the component sees it: the logout-everywhere mutation's own signature. */
type LogoutEverywhereMutate = UseMutateFunction<void, Error, void, unknown>

/** The context react-query threads into mutation callbacks. */
const mutationContext: MutationFunctionContext = {
  client: new QueryClient(),
  meta: undefined,
}

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

const mockMutate = vi.fn<LogoutEverywhereMutate>()
const mockSetQueryData = vi.fn<(key: unknown, data: unknown) => void>()
const mockNavigate = vi.fn<(options: { to: string }) => void>()

vi.mock('@/shared/auth/hooks/useLogoutEverywhere', () => ({
  useLogoutEverywhere: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}))

vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  authQueryKeys: {
    me: ['auth', 'me'],
  },
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

vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(() => mockNavigate),
}))

import { useLogoutEverywhere } from '@/shared/auth'

describe('LogoutEverywhereSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useLogoutEverywhere).mockReturnValue(
      buildMutationResult<void, Error, void, unknown>({
        mutate: mockMutate,
        isPending: false,
      }),
    )
  })

  it('renders section title and button from i18n', () => {
    render(<LogoutEverywhereSection />)

    expect(screen.getByRole('heading', { name: /log out everywhere/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log out everywhere/i })).toBeInTheDocument()
  })

  it('opens confirmation dialog when button is clicked', async () => {
    const user = userEvent.setup()
    render(<LogoutEverywhereSection />)

    await user.click(screen.getByRole('button', { name: /log out everywhere/i }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(/signed out of every device/i)).toBeInTheDocument()
  })

  it('confirm triggers mutation, shows success toast, clears auth cache, and redirects', async () => {
    const user = userEvent.setup()
    const mockMutateWithCallback = vi.fn<LogoutEverywhereMutate>((_, options) => {
      options?.onSuccess?.(undefined, undefined, undefined, mutationContext)
    })
    vi.mocked(useLogoutEverywhere).mockReturnValue(
      buildMutationResult<void, Error, void, unknown>({
        mutate: mockMutateWithCallback,
        isPending: false,
      }),
    )

    render(<LogoutEverywhereSection />)

    await user.click(screen.getByRole('button', { name: /log out everywhere/i }))

    const dialog = screen.getByRole('dialog')
    const confirmButton = within(dialog).getByRole('button', { name: /log out everywhere/i })
    await user.click(confirmButton)

    expect(mockMutateWithCallback).toHaveBeenCalled()
    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining('Signed out of all devices.'),
    )
    expect(mockSetQueryData).toHaveBeenCalledWith(['auth', 'me'], null)
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/' })
  })

  it('shows loading state while mutation is pending', async () => {
    const user = userEvent.setup()
    vi.mocked(useLogoutEverywhere).mockReturnValue(
      buildMutationResult<void, Error, void, unknown>({
        mutate: mockMutate,
        isPending: true,
      }),
    )

    render(<LogoutEverywhereSection />)

    await user.click(screen.getByRole('button', { name: /log out everywhere/i }))

    const dialog = screen.getByRole('dialog')
    const buttons = within(dialog).getAllByRole('button')
    buttons.forEach((button) => expect(button).toBeDisabled())
  })

  it('error path shows error toast and does not redirect', async () => {
    const user = userEvent.setup()
    const mockMutateWithError = vi.fn<LogoutEverywhereMutate>((_, options) => {
      options?.onError?.(new Error('network'), undefined, undefined, mutationContext)
    })
    vi.mocked(useLogoutEverywhere).mockReturnValue(
      buildMutationResult<void, Error, void, unknown>({
        mutate: mockMutateWithError,
        isPending: false,
      }),
    )

    render(<LogoutEverywhereSection />)

    await user.click(screen.getByRole('button', { name: /log out everywhere/i }))

    const dialog = screen.getByRole('dialog')
    const confirmButton = within(dialog).getByRole('button', { name: /log out everywhere/i })
    await user.click(confirmButton)

    expect(toast.error).toHaveBeenCalled()
    expect(mockNavigate).not.toHaveBeenCalled()
    expect(mockSetQueryData).not.toHaveBeenCalled()
  })

  it('renders without missing-key warnings across locales', async () => {
    const i18nModule = await import('@/lib/i18n')
    const i18n = i18nModule.default
    const keys = [
      'title',
      'description',
      'button',
      'confirmTitle',
      'confirmDescription',
      'confirmButton',
      'cancelButton',
      'success',
      'error',
    ]

    for (const lng of ['EN', 'KR', 'JP', 'CN']) {
      await i18n.changeLanguage(lng)
      for (const key of keys) {
        const fullKey = `settings.logoutEverywhere.${key}`
        expect(i18n.exists(fullKey)).toBe(true)
        expect(i18n.t(fullKey)).not.toBe(fullKey)
      }
    }
    await i18n.changeLanguage('EN')
  })
})
