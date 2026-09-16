/**
 * EmptyStatePlaceholder.test.tsx
 *
 * The placeholder is a button only when it can be clicked, is disabled in read-only
 * mode, and carries `selectable` only while a click would land.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'

import { EmptyStatePlaceholder } from '../EmptyStatePlaceholder'

describe('EmptyStatePlaceholder', () => {
  it('renders a div when no onClick is given', () => {
    render(<EmptyStatePlaceholder label="Nothing selected" />)

    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByText('Nothing selected').tagName).toBe('DIV')
  })

  it('renders a button when onClick is given', () => {
    render(<EmptyStatePlaceholder label="Select a pack" onClick={vi.fn()} />)

    const button = screen.getByRole('button', { name: 'Select a pack' })
    expect(button.tagName).toBe('BUTTON')
    expect(button).not.toBeDisabled()
  })

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    render(<EmptyStatePlaceholder label="Select a pack" onClick={onClick} />)

    await userEvent.click(screen.getByRole('button', { name: 'Select a pack' }))

    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('disables the button and swallows the click in read-only mode', async () => {
    const onClick = vi.fn()
    render(<EmptyStatePlaceholder label="No pack" onClick={onClick} readOnly />)

    const button = screen.getByRole('button', { name: 'No pack' })
    expect(button).toBeDisabled()

    await userEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('carries selectable only while clickable', () => {
    const { unmount } = render(<EmptyStatePlaceholder label="Select" onClick={vi.fn()} />)
    expect(screen.getByRole('button').className).toContain('selectable')
    unmount()

    const readOnly = render(<EmptyStatePlaceholder label="Select" onClick={vi.fn()} readOnly />)
    expect(screen.getByRole('button').className).not.toContain('selectable')
    readOnly.unmount()

    render(<EmptyStatePlaceholder label="Select" />)
    expect(screen.getByText('Select').className).not.toContain('selectable')
  })

  it('carries no width or height of its own and merges the parent class', () => {
    render(<EmptyStatePlaceholder label="Empty" className="size-full" />)

    const box = screen.getByText('Empty')
    expect(box.className).toContain('size-full')
    expect(box.className).toContain('border-dashed')
    expect(box.className).not.toMatch(/\b(w|h|min-h)-(\[|\d)/)
  })
})
