import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'

import { ConfirmActionDialog } from '../ConfirmActionDialog'

const baseProps = {
  open: true,
  onOpenChange: vi.fn(),
  title: 'Delete plan',
  description: 'This cannot be undone',
  cancelLabel: 'Cancel',
  confirmLabel: 'Delete',
  onConfirm: vi.fn(),
}

describe('ConfirmActionDialog', () => {
  it('renders title, description and both buttons', () => {
    render(<ConfirmActionDialog {...baseProps} />)

    expect(screen.getByText('Delete plan')).toBeInTheDocument()
    expect(screen.getByText('This cannot be undone')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  it('closes on cancel and calls onConfirm on confirm', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    const onConfirm = vi.fn()
    render(<ConfirmActionDialog {...baseProps} onOpenChange={onOpenChange} onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('prefers onCancel over closing when provided', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    const onOpenChange = vi.fn()
    render(<ConfirmActionDialog {...baseProps} onCancel={onCancel} onOpenChange={onOpenChange} />)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onOpenChange).not.toHaveBeenCalled()
  })

  it('swaps the confirm label and disables both buttons while pending', () => {
    render(<ConfirmActionDialog {...baseProps} isPending pendingLabel="Deleting…" />)

    expect(screen.getByRole('button', { name: 'Deleting…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })

  it('keeps the confirm label when pending without a pendingLabel', () => {
    render(<ConfirmActionDialog {...baseProps} isPending />)

    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
  })

  it('disables only the confirm button for confirmDisabled', () => {
    render(<ConfirmActionDialog {...baseProps} confirmDisabled />)

    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled()
  })

  it('renders the icon, extra children and the class-name slots', () => {
    render(
      <ConfirmActionDialog
        {...baseProps}
        destructive
        icon={<span data-testid="warning-icon" />}
        className="sm:max-w-md"
        descriptionClassName="pt-2"
        footerClassName="gap-2 sm:gap-0"
      >
        <p data-testid="extra">Permanent</p>
      </ConfirmActionDialog>,
    )

    expect(screen.getByTestId('warning-icon')).toBeInTheDocument()
    expect(screen.getByTestId('extra')).toBeInTheDocument()
    expect(screen.getByText('This cannot be undone')).toHaveClass('pt-2')
    expect(screen.getByRole('dialog')).toHaveClass('sm:max-w-md')
  })

  it('renders nothing while closed', () => {
    render(<ConfirmActionDialog {...baseProps} open={false} />)

    expect(screen.queryByText('Delete plan')).not.toBeInTheDocument()
  })
})
