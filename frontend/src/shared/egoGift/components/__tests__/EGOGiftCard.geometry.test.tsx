import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'

import { CARD_MOBILE_SCALE, LG_BREAKPOINT_PX } from '@/lib/constants'
import { CardSlot, EGO_GIFT_GEOMETRY, aspectOf } from '@/shared/cardLayout'
import { getKeywordIconPath } from '@/shared/assets'
import { EGO_GIFT_CARD, cqw, pct } from '../../lib/cardLayout'
import { EGOGiftCard } from '../EGOGiftCard'
import type { EGOGiftEntity } from '../../types/EGOGiftTypes'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}))

const GIFT = {
  id: '9001',
  name: 'Test Gift',
  tag: ['TIER_2'],
  keyword: 'Burn',
  maxEnhancement: 2,
} as unknown as EGOGiftEntity

function setViewport(width: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
}

function renderInSlot(enhancement: 0 | 1 | 2 = 0) {
  return render(
    <CardSlot size={EGO_GIFT_GEOMETRY.size}>
      <EGOGiftCard gift={GIFT} enhancement={enhancement} />
    </CardSlot>,
  )
}

describe('EGOGiftCard geometry', () => {
  it('fills its slot and carries the card ratio', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = renderInSlot()

    const slot = container.firstElementChild as HTMLElement
    expect(slot).toHaveStyle({
      width: `${String(EGO_GIFT_GEOMETRY.size.widthPx)}px`,
      aspectRatio: String(aspectOf(EGO_GIFT_GEOMETRY.size)),
    })

    const card = slot.firstElementChild as HTMLElement
    expect(card).toHaveClass('w-full')
    expect(card).toHaveStyle({
      containerType: 'inline-size',
      aspectRatio: String(aspectOf(EGO_GIFT_GEOMETRY.size)),
    })
  })

  it('shrinks with its slot below the breakpoint', () => {
    setViewport(LG_BREAKPOINT_PX - 1)

    const { container } = renderInSlot()

    expect(container.firstElementChild).toHaveStyle({
      width: `${String(EGO_GIFT_GEOMETRY.size.widthPx * CARD_MOBILE_SCALE)}px`,
    })
  })

  it('draws the gift icon at its transcribed share of the card', () => {
    const { container } = renderInSlot()

    const icon = container.querySelector('img[alt="EGO Gift 9001"]')
    expect(icon).toHaveStyle({
      width: pct(EGO_GIFT_CARD.icon.size),
      height: pct(EGO_GIFT_CARD.icon.size),
    })
  })

  it('draws the numeric tier at its transcribed share of the card', () => {
    const { container } = renderInSlot()

    const tier = [...container.querySelectorAll('div')]
      .filter((el) => el.textContent === 'II')
      .at(-1)
    expect(tier).toHaveStyle({
      fontSize: cqw(EGO_GIFT_CARD.tierText.fontSize),
      left: pct(EGO_GIFT_CARD.tierText.left),
    })
  })

  it('draws the enhancement badge at the share its level names', () => {
    const { container } = renderInSlot(2)

    const badge = container.querySelector('img[alt="+2"]')
    expect(badge).toHaveStyle({
      height: pct(EGO_GIFT_CARD.enhancement[2].height),
      top: pct(EGO_GIFT_CARD.enhancement[2].top),
      right: pct(EGO_GIFT_CARD.enhancement[2].right),
    })
  })

  it('draws the keyword badge at its transcribed share of the card', () => {
    const { container } = renderInSlot()

    const badge = container.querySelector(`img[src="${getKeywordIconPath('Burn')}"]`)
    expect(badge).toHaveStyle({ height: pct(EGO_GIFT_CARD.keywordIcon) })
  })
})
