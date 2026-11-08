import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { CARD_GAP_PX } from '@/lib/constants'
import { IDENTITY_GEOMETRY } from '@/shared/cardLayout'
import { ListPageSkeleton } from '../ListPageSkeleton'

const { widthPx, heightPx } = IDENTITY_GEOMETRY.size
const expectedTiles =
  Math.ceil(window.innerWidth / (widthPx + CARD_GAP_PX)) *
  Math.ceil(window.innerHeight / (heightPx + CARD_GAP_PX))

describe('ListPageSkeleton', () => {
  it('draws a slot-sized placeholder per tile by default', () => {
    const { container } = render(<ListPageSkeleton geometry={IDENTITY_GEOMETRY} />)

    const tiles = container.querySelectorAll<HTMLElement>('[data-slot="skeleton"][style*="width"]')

    expect(tiles).toHaveLength(expectedTiles)
    expect(tiles[0]?.style.width).toBe(`${widthPx}px`)
    expect(tiles[0]?.style.height).toBe(`${heightPx}px`)
  })

  it('draws the given card in each tile with the slot size', () => {
    const { container } = render(
      <ListPageSkeleton
        geometry={IDENTITY_GEOMETRY}
        card={(slot) => <i data-testid="tile">{`${slot.widthPx}x${slot.heightPx}`}</i>}
      />,
    )

    const tiles = container.querySelectorAll('[data-testid="tile"]')

    expect(tiles).toHaveLength(expectedTiles)
    expect(tiles[0]?.textContent).toBe(`${widthPx}x${heightPx}`)
    expect(container.querySelectorAll('[data-slot="skeleton"][style*="width"]')).toHaveLength(0)
  })
})
