import { describe, it, expect, vi } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import { LinkDialog } from '../LinkDialog'

function renderWithUrl() {
  const onConfirm = vi.fn()
  render(<LinkDialog open onClose={vi.fn()} onConfirm={onConfirm} />)
  const urlInput = document.getElementById('link-url') as HTMLInputElement
  const textInput = document.getElementById('link-text') as HTMLInputElement
  fireEvent.change(urlInput, { target: { value: 'https://example.com' } })
  fireEvent.change(textInput, { target: { value: '링크' } })
  return { onConfirm, textInput }
}

describe('LinkDialog — Enter during IME composition', () => {
  it('does not confirm when Enter fires while the display text is being composed', () => {
    const { onConfirm, textInput } = renderWithUrl()

    fireEvent.keyDown(textInput, { key: 'Enter', isComposing: true })

    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('confirms when Enter fires outside composition', () => {
    const { onConfirm, textInput } = renderWithUrl()

    fireEvent.keyDown(textInput, { key: 'Enter', isComposing: false })

    expect(onConfirm).toHaveBeenCalledWith('https://example.com', '링크')
  })
})
