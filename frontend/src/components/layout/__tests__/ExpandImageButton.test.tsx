import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ExpandImageButton } from '../ExpandImageButton'

const SRC = '/images/ego/20101/20101_cg.webp'
const ALT = 'Test EGO'

describe('ExpandImageButton', () => {
  it('opens the lightbox with the image on click', () => {
    render(<ExpandImageButton src={SRC} alt={ALT} />)

    expect(screen.queryByRole('dialog')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: /expand image/i }))

    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeDefined()
    const image = screen.getByAltText(ALT)
    expect(image.getAttribute('src')).toBe(SRC)
  })

  it('closes the lightbox via the close button', () => {
    render(<ExpandImageButton src={SRC} alt={ALT} />)

    fireEvent.click(screen.getByRole('button', { name: /expand image/i }))
    expect(screen.getByRole('dialog')).toBeDefined()

    fireEvent.click(screen.getByRole('button', { name: /^close$/i }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('closes the lightbox on Escape', () => {
    render(<ExpandImageButton src={SRC} alt={ALT} />)

    fireEvent.click(screen.getByRole('button', { name: /expand image/i }))
    expect(screen.getByRole('dialog')).toBeDefined()

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
