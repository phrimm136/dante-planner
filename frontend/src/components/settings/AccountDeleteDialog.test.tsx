import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'settings.deleteAccount.title': 'Delete Account',
        'settings.deleteAccount.confirmMessage': 'This will permanently delete your account and all associated data.',
        'settings.deleteAccount.typeToConfirm': 'Type <1>DELETE</1> to confirm',
        'settings.deleteAccount.cannotUndo': 'This action cannot be undone after the grace period expires.',
        'settings.deleteAccount.gracePeriod': 'You have a 30-day grace period to cancel deletion.',
        'cancel': 'Cancel',
        'deleting': 'Deleting...',
      }
      return translations[key] ?? key
    },
  }),
  Trans: ({ i18nKey, components }: { i18nKey: string; components?: Record<string, React.ReactElement> }) => {
    if (i18nKey === 'settings.deleteAccount.typeToConfirm' && components) {
      return (
        <>
          Type {components[1] ? <span className="font-bold text-destructive">DELETE</span> : 'DELETE'} to confirm
        </>
      )
    }
    return i18nKey
  },
}))

import { AccountDeleteDialog } from './AccountDeleteDialog'

describe('AccountDeleteDialog', () => {
  const mockOnConfirm = vi.fn()
  const mockOnCancel = vi.fn()

  const defaultProps = {
    open: true,
    onConfirm: mockOnConfirm,
    onCancel: mockOnCancel,
    isPending: false,
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders dialog when open', () => {
    render(<AccountDeleteDialog {...defaultProps} />)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Delete Account' })).toBeInTheDocument()
    expect(
      screen.getByText(/permanently delete your account/i)
    ).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(<AccountDeleteDialog {...defaultProps} open={false} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('updates confirmation input on user typing', async () => {
    const user = userEvent.setup()
    render(<AccountDeleteDialog {...defaultProps} />)

    const input = screen.getByPlaceholderText('DELETE')
    await user.type(input, 'DELETE')

    expect(input).toHaveValue('DELETE')
  })

  it('disables delete button when input is empty', () => {
    render(<AccountDeleteDialog {...defaultProps} />)

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    expect(deleteButton).toBeDisabled()
  })

  it('disables delete button when input is incorrect', async () => {
    const user = userEvent.setup()
    render(<AccountDeleteDialog {...defaultProps} />)

    const input = screen.getByPlaceholderText('DELETE')
    await user.type(input, 'delete') // lowercase

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    expect(deleteButton).toBeDisabled()
  })

  it('enables delete button when input is exactly "DELETE"', async () => {
    const user = userEvent.setup()
    render(<AccountDeleteDialog {...defaultProps} />)

    const input = screen.getByPlaceholderText('DELETE')
    await user.type(input, 'DELETE')

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    expect(deleteButton).toBeEnabled()
  })

  it('calls onConfirm when delete button clicked', async () => {
    const user = userEvent.setup()
    render(<AccountDeleteDialog {...defaultProps} />)

    const input = screen.getByPlaceholderText('DELETE')
    await user.type(input, 'DELETE')

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    await user.click(deleteButton)

    expect(mockOnConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when cancel button clicked', async () => {
    const user = userEvent.setup()
    render(<AccountDeleteDialog {...defaultProps} />)

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await user.click(cancelButton)

    expect(mockOnCancel).toHaveBeenCalledTimes(1)
  })

  it('disables both buttons when isPending is true', () => {
    render(<AccountDeleteDialog {...defaultProps} isPending={true} />)

    const deleteButton = screen.getByRole('button', { name: /deleting/i })
    const cancelButton = screen.getByRole('button', { name: /cancel/i })

    expect(deleteButton).toBeDisabled()
    expect(cancelButton).toBeDisabled()
  })

  it('shows "Deleting..." text when isPending is true', () => {
    render(<AccountDeleteDialog {...defaultProps} isPending={true} />)

    expect(screen.getByText('Deleting...')).toBeInTheDocument()
  })

  it('disables input when isPending is true', () => {
    render(<AccountDeleteDialog {...defaultProps} isPending={true} />)

    const input = screen.getByPlaceholderText('DELETE')
    expect(input).toBeDisabled()
  })

  it('prevents ESC key dismissal', async () => {
    const user = userEvent.setup()
    render(<AccountDeleteDialog {...defaultProps} />)

    await user.keyboard('{Escape}')

    // Dialog should still be open (onCancel not called via ESC)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(mockOnCancel).not.toHaveBeenCalled()
  })

  it('shows grace period information', () => {
    render(<AccountDeleteDialog {...defaultProps} />)

    expect(screen.getByText(/cannot be undone after the grace period/i)).toBeInTheDocument()
  })

  it('shows destructive styling on delete button', () => {
    render(<AccountDeleteDialog {...defaultProps} />)

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    // Button has destructive variant class (implementation detail, but important for UX)
    expect(deleteButton.className).toContain('destructive')
  })
})
