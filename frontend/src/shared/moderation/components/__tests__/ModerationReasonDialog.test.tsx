import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'

import { ModerationReasonDialog } from '../ModerationReasonDialog'

const baseProps = {
  open: true,
  onOpenChange: vi.fn(),
  title: 'Ban user',
  description: 'Ban Faust',
  reasonLabel: 'Reason',
  reasonPlaceholder: 'Why?',
  reasonInputId: 'ban-reason',
  cancelLabel: 'Cancel',
  confirmLabel: 'Ban',
  onConfirm: vi.fn(),
  isPending: false,
}

describe('ModerationReasonDialog', () => {
  it('ties the label to the textarea and shows the character counter', () => {
    render(<ModerationReasonDialog {...baseProps} />)

    const textarea = screen.getByLabelText('Reason')
    expect(textarea).toHaveAttribute('id', 'ban-reason')
    expect(textarea).toHaveAttribute('maxlength', '500')
    expect(screen.getByText('0/500')).toBeInTheDocument()
  })

  it('keeps confirm disabled until the reason has content, cancel stays enabled', async () => {
    const user = userEvent.setup()
    render(<ModerationReasonDialog {...baseProps} />)

    expect(screen.getByRole('button', { name: 'Ban' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled()

    await user.type(screen.getByLabelText('Reason'), '   ')
    expect(screen.getByRole('button', { name: 'Ban' })).toBeDisabled()

    await user.type(screen.getByLabelText('Reason'), 'spam')
    expect(screen.getByRole('button', { name: 'Ban' })).toBeEnabled()
  })

  it('passes the reason up and clears it on confirm', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<ModerationReasonDialog {...baseProps} onConfirm={onConfirm} />)

    await user.type(screen.getByLabelText('Reason'), 'spam')
    await user.click(screen.getByRole('button', { name: 'Ban' }))

    expect(onConfirm).toHaveBeenCalledWith('spam')
    expect(screen.getByLabelText('Reason')).toHaveValue('')
  })

  it('clears the reason and closes on cancel', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    render(<ModerationReasonDialog {...baseProps} onOpenChange={onOpenChange} />)

    await user.type(screen.getByLabelText('Reason'), 'spam')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onOpenChange).toHaveBeenCalledWith(false)
    expect(screen.getByLabelText('Reason')).toHaveValue('')
  })

  it('disables both buttons while pending', () => {
    render(<ModerationReasonDialog {...baseProps} isPending />)

    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Ban' })).toBeDisabled()
  })

  it('groups extra controls with the reason field', () => {
    render(
      <ModerationReasonDialog {...baseProps}>
        <div data-testid="duration-presets" />
      </ModerationReasonDialog>,
    )

    const extras = screen.getByTestId('duration-presets')
    expect(extras.parentElement).toHaveClass('space-y-4')
    expect(extras.parentElement).toContainElement(screen.getByLabelText('Reason'))
  })
})
