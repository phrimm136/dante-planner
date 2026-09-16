/**
 * EGOListSkeleton.test.tsx
 *
 * Tests that the EGO list placeholder is stencilled by the card's mask sprite.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { EGOListSkeleton } from '../EGOListSkeleton'

vi.mock('@/shared/assets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/assets')>()),
  getEGOMaskPath: () => '/mock/mask.png',
}))

describe('EGOListSkeleton', () => {
  it('draws each placeholder with the mask sprite', () => {
    const { container } = render(<EGOListSkeleton cardCount={1} />)

    const placeholder = container.querySelector<HTMLElement>(
      '[data-slot="page-skeleton"] .relative > *',
    )

    expect(placeholder).not.toBeNull()
    expect(placeholder?.style.maskImage).toContain('mask')
  })
})
