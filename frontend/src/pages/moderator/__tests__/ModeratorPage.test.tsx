import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import ModeratorPage from '../ModeratorPage'

const mocks = vi.hoisted(() => {
  const runSuccess = (_vars: unknown, opts?: { onSuccess?: () => void }) => opts?.onSuccess?.()
  return {
    banMutate: vi.fn(runSuccess),
    unbanMutate: vi.fn(runSuccess),
    timeoutMutate: vi.fn(runSuccess),
    untimeoutMutate: vi.fn(runSuccess),
  }
})

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  }
})

vi.mock('@/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/shared/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/auth')>()),
  useAuthQuery: () => ({ data: { role: 'ADMIN', usernameSuffix: '9999' } }),
}))

vi.mock('../hooks/useModeratorData', () => ({
  useModeratorUsers: () => [
    {
      usernameEpithet: 'Target',
      usernameSuffix: '1234',
      role: 'NORMAL',
      isBanned: false,
      isTimedOut: false,
    },
  ],
  useModerationHistory: () => [],
}))

vi.mock('../hooks/useModeratorMutations', () => ({
  useBanUser: () => ({ mutate: mocks.banMutate, isPending: false }),
  useUnbanUser: () => ({ mutate: mocks.unbanMutate, isPending: false }),
  useTimeoutUser: () => ({ mutate: mocks.timeoutMutate, isPending: false }),
  useUntimeoutUser: () => ({ mutate: mocks.untimeoutMutate, isPending: false }),
}))

interface ReasonDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: (reason: string) => void
}

interface TimeoutDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: (durationMinutes: number, reason: string) => void
}

function dialogBody(testId: string, confirm: () => void, close: () => void) {
  return (
    <div data-testid={testId}>
      <button type="button" onClick={confirm}>{`confirm-${testId}`}</button>
      <button type="button" onClick={close}>{`close-${testId}`}</button>
    </div>
  )
}

function reasonDialogStub(testId: string) {
  return ({ open, onOpenChange, onConfirm }: ReasonDialogProps) =>
    open
      ? dialogBody(
          testId,
          () => onConfirm('reason'),
          () => onOpenChange(false),
        )
      : null
}

function timeoutDialogStub(testId: string) {
  return ({ open, onOpenChange, onConfirm }: TimeoutDialogProps) =>
    open
      ? dialogBody(
          testId,
          () => onConfirm(10, 'reason'),
          () => onOpenChange(false),
        )
      : null
}

vi.mock('@/shared/moderation', () => ({
  BanDialog: reasonDialogStub('ban-dialog'),
  UnbanDialog: reasonDialogStub('unban-dialog'),
  TimeoutDialog: timeoutDialogStub('timeout-dialog'),
  ClearTimeoutDialog: reasonDialogStub('clear-timeout-dialog'),
}))

const ALL_DIALOGS = ['ban-dialog', 'unban-dialog', 'timeout-dialog', 'clear-timeout-dialog']

function openDialogs() {
  return ALL_DIALOGS.filter((id) => screen.queryByTestId(id) !== null)
}

describe('ModeratorPage user row dialogs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens no dialog until an action button is clicked', () => {
    render(<ModeratorPage />)

    expect(openDialogs()).toEqual([])
  })

  it('opens only the ban dialog when ban is clicked', async () => {
    const user = userEvent.setup()
    render(<ModeratorPage />)

    await user.click(screen.getByText('dashboard.ban'))

    expect(openDialogs()).toEqual(['ban-dialog'])
  })

  it('replaces the open dialog when a different action is clicked', async () => {
    const user = userEvent.setup()
    render(<ModeratorPage />)

    await user.click(screen.getByText('dashboard.ban'))
    expect(openDialogs()).toEqual(['ban-dialog'])

    await user.click(screen.getByText('dashboard.timeout'))

    expect(openDialogs()).toEqual(['timeout-dialog'])
  })

  it('closes the ban dialog once the ban succeeds', async () => {
    const user = userEvent.setup()
    render(<ModeratorPage />)

    await user.click(screen.getByText('dashboard.ban'))
    await user.click(screen.getByText('confirm-ban-dialog'))

    expect(mocks.banMutate).toHaveBeenCalled()
    expect(openDialogs()).toEqual([])
  })

  it('closes the timeout dialog when the dialog requests close', async () => {
    const user = userEvent.setup()
    render(<ModeratorPage />)

    await user.click(screen.getByText('dashboard.timeout'))
    await user.click(screen.getByText('close-timeout-dialog'))

    expect(openDialogs()).toEqual([])
  })
})
