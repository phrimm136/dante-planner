import { describe, it, expect, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'

import { CARD_MOBILE_SCALE, CARD_MOBILE_SCALE_DENSE, LG_BREAKPOINT_PX } from '@/lib/constants'
import { CardSlot } from '../CardSlot'

const IDENTITY_SIZE = { widthPx: 160, heightPx: 232 }
const BOXED_240 = { widthPx: 240, heightPx: 300 }

function setViewport(widthPx: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: widthPx })
}

afterEach(() => {
  setViewport(1024)
})

function slotOf(container: HTMLElement): HTMLElement {
  return container.firstElementChild as HTMLElement
}

describe('CardSlot', () => {
  it('lays the slot out at its desktop width above the breakpoint', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = render(
      <CardSlot size={BOXED_240}>
        <span />
      </CardSlot>,
    )

    expect(slotOf(container)).toHaveStyle({ width: '240px' })
  })

  it('is the query container the card it holds resolves its own lengths against', () => {
    const { container } = render(
      <CardSlot size={BOXED_240}>
        <span />
      </CardSlot>,
    )

    expect(slotOf(container)).toHaveStyle({ containerType: 'inline-size' })
  })

  it('scales the slot down below the breakpoint', () => {
    setViewport(LG_BREAKPOINT_PX - 1)

    const { container } = render(
      <CardSlot size={BOXED_240}>
        <span />
      </CardSlot>,
    )

    expect(slotOf(container)).toHaveStyle({
      width: `${String(240 * CARD_MOBILE_SCALE)}px`,
    })
  })

  it('takes the scale it is given', () => {
    setViewport(LG_BREAKPOINT_PX - 1)

    const { container } = render(
      <CardSlot size={BOXED_240} mobileScale={CARD_MOBILE_SCALE_DENSE}>
        <span />
      </CardSlot>,
    )

    expect(slotOf(container)).toHaveStyle({
      width: `${String(240 * CARD_MOBILE_SCALE_DENSE)}px`,
    })
  })

  it('pins its height to the aspect of the card box it is given', () => {
    const { container } = render(
      <CardSlot size={IDENTITY_SIZE}>
        <span />
      </CardSlot>,
    )

    expect(slotOf(container)).toHaveStyle({ aspectRatio: String(160 / 232) })
  })

  it('holds the card it is given', () => {
    render(
      <CardSlot size={BOXED_240}>
        <span data-testid="card" />
      </CardSlot>,
    )

    expect(screen.getByTestId('card')).toBeInTheDocument()
  })

  it('carries the class it is given', () => {
    const { container } = render(
      <CardSlot size={BOXED_240} className="hidden">
        <span />
      </CardSlot>,
    )

    expect(slotOf(container)).toHaveClass('hidden')
  })
})
