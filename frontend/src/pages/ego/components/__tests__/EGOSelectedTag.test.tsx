import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'

import { EGO_SELECTED_TAG } from '../../lib/cardLayout'
import { EGOSelectedTag } from '../EGOSelectedTag'

vi.mock('@/shared/assets', () => ({
  getFormationBadgePath: (state: string) => `/mock/deploy-${state}.webp`,
}))

describe('EGOSelectedTag', () => {
  it('stretches the SELECTED plate across its transcribed rect', () => {
    const { container } = render(<EGOSelectedTag />)
    const tag = container.querySelector('img') as HTMLImageElement

    expect(tag.getAttribute('src')).toBe('/mock/deploy-selected.webp')
    expect(tag.style.left).toBe(`${String(EGO_SELECTED_TAG.rect.left)}%`)
    expect(tag.style.top).toBe(`${String(EGO_SELECTED_TAG.rect.top)}%`)
    expect(tag.style.width).toBe(`${String(EGO_SELECTED_TAG.rect.width)}%`)
    expect(tag.style.height).toBe(`${String(EGO_SELECTED_TAG.rect.height)}%`)
    expect(tag.style.objectFit).toBe('fill')
  })
})
