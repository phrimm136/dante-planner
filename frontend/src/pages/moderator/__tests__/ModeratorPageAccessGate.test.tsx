import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import ModeratorPage from '../ModeratorPage'

const mocks = vi.hoisted(() => ({
  role: { current: 'NORMAL' as string },
  useModeratorUsers: vi.fn(() => []),
  useModerationHistory: vi.fn(() => []),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  }
})

vi.mock('@/shared/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/auth')>()),
  useAuthQuery: () => ({ data: { role: mocks.role.current, usernameSuffix: '1234' } }),
}))

vi.mock('../hooks/useModeratorData', () => ({
  useModeratorUsers: mocks.useModeratorUsers,
  useModerationHistory: mocks.useModerationHistory,
}))

vi.mock('../hooks/useModeratorMutations', () => ({
  useBanUser: () => ({ mutate: vi.fn(), isPending: false }),
  useUnbanUser: () => ({ mutate: vi.fn(), isPending: false }),
  useTimeoutUser: () => ({ mutate: vi.fn(), isPending: false }),
  useUntimeoutUser: () => ({ mutate: vi.fn(), isPending: false }),
}))

describe('ModeratorPage staff gate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('denies a non-staff visitor without reaching the moderation queries', () => {
    mocks.role.current = 'NORMAL'

    render(<ModeratorPage />)

    expect(screen.getByText('dashboard.accessDenied')).toBeInTheDocument()
    expect(mocks.useModeratorUsers).not.toHaveBeenCalled()
    expect(mocks.useModerationHistory).not.toHaveBeenCalled()
  })

  it('runs the moderation queries for staff', () => {
    mocks.role.current = 'ADMIN'

    render(<ModeratorPage />)

    expect(screen.queryByText('dashboard.accessDenied')).toBeNull()
    expect(mocks.useModeratorUsers).toHaveBeenCalled()
    expect(mocks.useModerationHistory).toHaveBeenCalled()
  })
})
