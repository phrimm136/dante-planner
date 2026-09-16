import { describe, it, expect, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'

import type { CardSizePx } from '@/shared/cardLayout'
import { createTestFilterStore } from '@/test-utils/filterStore'
import { FilteredCardSlot } from '../FilteredCardSlot'

interface State {
  hidden: boolean
}

function setViewport(widthPx: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: widthPx })
}

afterEach(() => {
  setViewport(1024)
})

const BOXED_310: CardSizePx = { widthPx: 310, heightPx: 450 }

function renderSlot(props: { size?: CardSizePx; visible?: boolean } = {}) {
  const store = createTestFilterStore<State>({ hidden: props.visible === false })

  return render(
    <FilteredCardSlot<State>
      store={store}
      selectVisible={(state) => !state.values.hidden}
      size={props.size ?? BOXED_310}
      mobileScale={0.5}
    >
      <span data-testid="card" />
    </FilteredCardSlot>,
  )
}

describe('FilteredCardSlot', () => {
  it('renders the card as its only child', () => {
    const { container } = renderSlot()

    const slot = container.firstElementChild as HTMLElement
    expect(slot.children).toHaveLength(1)
    expect(slot.firstElementChild).toBe(screen.getByTestId('card'))
  })

  it('takes the card width on desktop', () => {
    setViewport(1280)

    const { container } = renderSlot()

    expect(container.firstElementChild).toHaveStyle({ width: '310px' })
  })

  it('takes the scaled card width below the desktop breakpoint', () => {
    setViewport(720)

    const { container } = renderSlot()

    expect(container.firstElementChild).toHaveStyle({ width: '155px' })
  })

  it('hides the slot without giving up its seat when the card is filtered out', () => {
    const { container } = renderSlot({
      visible: false,
      size: { widthPx: 310, heightPx: 450 },
    })

    const slot = container.firstElementChild as HTMLElement
    expect(slot).toHaveClass('hidden')
    expect(slot).toHaveStyle({ width: '310px', aspectRatio: String(310 / 450) })
  })
})
