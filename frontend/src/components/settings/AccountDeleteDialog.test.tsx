import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
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

    expect(screen.getByText(/30-day grace period/i)).toBeInTheDocument()
    expect(screen.getByText(/cannot be undone after the grace period/i)).toBeInTheDocument()
  })

  it('shows destructive styling on delete button', () => {
    render(<AccountDeleteDialog {...defaultProps} />)

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    // Button has destructive variant class (implementation detail, but important for UX)
    expect(deleteButton.className).toContain('destructive')
  })
})
