import { describe, it, expect, afterEach } from 'vitest'
import { render } from '@testing-library/react'

import { ResponsiveCardGrid } from '../ResponsiveCardGrid'

const DESKTOP_WIDTH = 1280
const MOBILE_WIDTH = 720

const BOXED_200 = { widthPx: 200, heightPx: 400 }
const BOXED_310 = { widthPx: 310, heightPx: 450 }

function setViewport(widthPx: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: widthPx })
}

afterEach(() => {
  setViewport(1024)
})

describe('ResponsiveCardGrid', () => {
  it('lays columns out at the card width on desktop', () => {
    setViewport(DESKTOP_WIDTH)

    const { container } = render(
      <ResponsiveCardGrid size={BOXED_200} mobileScale={0.5}>
        <span />
      </ResponsiveCardGrid>,
    )

    expect(container.querySelector('div.grid')).toHaveStyle({
      gridTemplateColumns: 'repeat(auto-fill, 200px)',
    })
  })

  it('scales the column width down below the desktop breakpoint', () => {
    setViewport(MOBILE_WIDTH)

    const { container } = render(
      <ResponsiveCardGrid size={BOXED_200} mobileScale={0.5}>
        <span />
      </ResponsiveCardGrid>,
    )

    expect(container.querySelector('div.grid')).toHaveStyle({
      gridTemplateColumns: 'repeat(auto-fill, 100px)',
    })
  })

  it('leaves rows implicit when it is told to size them from their content', () => {
    const { container } = render(
      <ResponsiveCardGrid size={BOXED_200} rows="content">
        <span />
      </ResponsiveCardGrid>,
    )

    expect((container.querySelector('div.grid') as HTMLElement).style.gridAutoRows).toBe('')
  })

  it('derives the row height from the column width and the card box', () => {
    setViewport(DESKTOP_WIDTH)

    const { container } = render(
      <ResponsiveCardGrid size={BOXED_310}>
        <span />
      </ResponsiveCardGrid>,
    )

    expect(container.querySelector('div.grid')).toHaveStyle({ gridAutoRows: '450px' })
  })

  it('shrinks the row height with the column below the breakpoint', () => {
    setViewport(MOBILE_WIDTH)

    const { container } = render(
      <ResponsiveCardGrid size={BOXED_310} mobileScale={0.5}>
        <span />
      </ResponsiveCardGrid>,
    )

    expect(container.querySelector('div.grid')).toHaveStyle({ gridAutoRows: '225px' })
  })
})
