/**
 * EGOListSkeleton.test.tsx
 *
 * Tests that the EGO list placeholder is stencilled by the card's mask sprite and that it
 * draws as many tiles as fill the viewport.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { CARD_GAP_PX } from '@/lib/constants'
import { EGOListSkeleton } from '../EGOListSkeleton'
import { EGO_GEOMETRY } from '@/shared/cardLayout'

vi.mock('@/shared/assets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/assets')>()),
  getEGOMaskPath: () => '/mock/mask.png',
}))

describe('EGOListSkeleton', () => {
  it('draws each placeholder with the mask sprite', () => {
    const { container } = render(<EGOListSkeleton />)

    const placeholder = container.querySelector<HTMLElement>(
      '[data-slot="page-skeleton"] .relative > *',
    )

    expect(placeholder).not.toBeNull()
    expect(placeholder?.style.maskImage).toContain('mask')
  })

  it('draws the tiles that fill the viewport', () => {
    const { widthPx, heightPx } = EGO_GEOMETRY.size
    const expected =
      Math.ceil(window.innerWidth / (widthPx + CARD_GAP_PX)) *
      Math.ceil(window.innerHeight / (heightPx + CARD_GAP_PX))

    const { container } = render(<EGOListSkeleton />)

    expect(container.querySelectorAll('[data-slot="page-skeleton"] .relative')).toHaveLength(
      expected,
    )
  })
})
