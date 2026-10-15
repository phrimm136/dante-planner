import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { KeywordCard } from '../KeywordCard'

function getFrame(container: HTMLElement) {
  return container.querySelector('img')?.parentElement
}

describe('KeywordCard', () => {
  it('highlights instantly on hover when enabled', () => {
    const { container } = render(<KeywordCard id="Combustion" iconId={null} enableHoverHighlight />)

    expect(getFrame(container)).toHaveClass('selectable', '[--selectable-transition-duration:0ms]')
  })

  it('carries no highlight when disabled', () => {
    const { container } = render(<KeywordCard id="Combustion" iconId={null} />)

    expect(getFrame(container)).not.toHaveClass('selectable')
  })
})
