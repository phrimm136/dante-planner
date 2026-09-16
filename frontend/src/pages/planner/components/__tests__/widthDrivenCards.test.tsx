import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { CARD_MOBILE_SCALE, LG_BREAKPOINT_PX } from '@/lib/constants'
import {
  COMPACT_IDENTITY_GEOMETRY,
  KEYWORD_ICON_GEOMETRY,
  SINNER_SKILL_GEOMETRY,
  SKILL_EXCHANGE_GEOMETRY,
  SKILL_IMAGE_GEOMETRY,
} from '../../lib/cardLayout'
import { EGO_GIFT_GEOMETRY } from '@/pages/egoGift'
import { IDENTITY_GEOMETRY } from '@/pages/identity'
import { CardSlot, aspectOf } from '@/shared/cardLayout'
import {
  COMPACT_IDENTITY_CARD,
  DECK_CARD,
  KEYWORD_ICON_CARD,
  SINNER_SKILL_CARD,
  SKILL_EXCHANGE_CARD,
  SINNER_GRID_COLUMNS,
  SINNER_GRID_GAP,
  SKILL_IMAGE_CARD,
  cqw,
  pct,
} from '../../lib/cardLayout'
import { StartGiftKeywordIcon } from '../startGift/StartGiftKeywordIcon'
import { SinnerSkillCard } from '../skillReplacement/SinnerSkillCard'
import { SkillImageSimple } from '../skillReplacement/SkillImageSimple'
import { SkillEADisplay } from '../skillReplacement/SkillEADisplay'
import { SkillExchangePane } from '../skillReplacement/SkillExchangePane'
import { SinnerDeckCard } from '../deckBuilder/SinnerDeckCard'
import { SinnerGrid } from '../deckBuilder/SinnerGrid'
import { SkillExchangeModal } from '../skillReplacement/SkillExchangeModal'
import { CompactIdentityRow } from '../deckBuilder/CompactIdentityRow'
import { EGOGiftCard } from '@/pages/egoGift'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}))

/**
 * jsdom resolves no container query, so each card is checked against the table it reads:
 * the root's own sizing, and one inner part per card carrying a transcribed share.
 */

function root(container: HTMLElement): HTMLElement {
  return container.firstElementChild as HTMLElement
}

function setViewport(width: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
}

const SKILL_INFOS = [
  { attributeType: 'CRIMSON' },
  { attributeType: 'SCARLET' },
  { attributeType: 'AMBER' },
] as const

describe('width-driven planner cards', () => {
  it('sizes the start-gift keyword icon off its square box', () => {
    const { container } = render(<StartGiftKeywordIcon keyword="Burn" />)

    expect(root(container)).toHaveClass('w-full', 'aspect-square')
    expect(container.querySelector('img')).toHaveStyle({
      width: pct(KEYWORD_ICON_CARD.icon),
      height: pct(KEYWORD_ICON_CARD.icon),
    })
  })

  it('sizes the sinner skill card off its own rect', () => {
    const { container } = render(
      <SinnerSkillCard
        identityId="10101"
        uptie={4}
        rank={3}
        skillInfos={[...SKILL_INFOS] as never}
        skillEA={{ 0: 3, 1: 2, 2: 1 } as never}
        onClick={() => undefined}
      />,
    )

    expect(root(container)).toHaveStyle({
      aspectRatio: String(aspectOf(SINNER_SKILL_GEOMETRY.size)),
      gap: cqw(SINNER_SKILL_CARD.rowGap),
    })
    expect(root(container).style.containerType).toBe('')
    expect(container.querySelector('img')?.parentElement?.parentElement).toHaveStyle({
      width: cqw(SINNER_SKILL_CARD.portrait),
    })
  })

  it('sizes the skill image off its own rect', () => {
    const { container } = render(
      <SkillImageSimple skillImagePath="/skill.webp" attributeType="CRIMSON" skillTier={1} />,
    )

    expect(root(container)).toHaveStyle({
      containerType: 'inline-size',
      aspectRatio: String(aspectOf(SKILL_IMAGE_GEOMETRY.size)),
    })
    expect(container.querySelector('img[src="/skill.webp"]')?.parentElement).toHaveStyle({
      width: pct(SKILL_IMAGE_CARD.art),
      height: pct(SKILL_IMAGE_CARD.art),
    })
  })

  it('hangs the EA badge off the skill image at its transcribed share', () => {
    const { container } = render(
      <SkillEADisplay identityId="10101" skillSlot={0} attributeType="CRIMSON" ea={3} />,
    )

    const badge = container.querySelector('.bg-primary')
    expect(badge).toHaveStyle({
      width: cqw(SKILL_IMAGE_CARD.badge),
      height: cqw(SKILL_IMAGE_CARD.badge),
      fontSize: cqw(SKILL_IMAGE_CARD.badgeFontSize),
    })
  })

  it('sizes the exchange pane off its own rect', () => {
    const { container } = render(
      <SkillExchangePane
        identityId="10101"
        sourceSlot={0}
        targetSlot={1}
        sourceAttributeType="CRIMSON"
        targetAttributeType="SCARLET"
        sourceEA={3}
        onClick={() => undefined}
      />,
    )

    expect(root(container)).toHaveStyle({
      aspectRatio: String(aspectOf(SKILL_EXCHANGE_GEOMETRY.size)),
      gap: cqw(SKILL_EXCHANGE_CARD.gap),
    })
    expect(root(container).style.containerType).toBe('')
    expect(root(container).firstElementChild).toHaveStyle({
      width: cqw(SKILL_EXCHANGE_CARD.skill),
    })
  })

  it('gives the deck composite no height of its own and slots its identity card', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <SinnerDeckCard
          sinnerName="Yi Sang"
          sinnerIndex={0}
          equipment={{ identity: { id: '10101', uptie: 4, level: 45 }, egos: {} } as never}
          identityData={undefined}
          skillData={{ affinities: [], atkTypes: [] }}
          egoAffinityMap={{}}
          deploymentOrder={null}
          mobileScale={CARD_MOBILE_SCALE}
        />
      </QueryClientProvider>,
    )

    const composite = root(container)
    expect(composite).toHaveStyle({ gap: cqw(DECK_CARD.rowGap) })
    expect(composite.style.containerType).toBe('')
    expect(composite.style.aspectRatio).toBe('')

    const slot = composite.querySelector('button > div') as HTMLElement
    expect(slot).toHaveStyle({
      width: `${String(IDENTITY_GEOMETRY.size.widthPx)}px`,
      aspectRatio: String(aspectOf(IDENTITY_GEOMETRY.size)),
    })
  })

  it('slots each compact identity cell at its own rect', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = render(
      <CompactIdentityRow
        equipment={{ '1': { identity: { id: '10101', uptie: 4, level: 45 }, egos: {} } } as never}
        deploymentOrder={[]}
        skillDataMap={{}}
      />,
    )

    const slot = root(container).firstElementChild as HTMLElement
    expect(slot).toHaveStyle({
      width: `${String(COMPACT_IDENTITY_GEOMETRY.size.widthPx)}px`,
      aspectRatio: String(aspectOf(COMPACT_IDENTITY_GEOMETRY.size)),
    })
    expect(slot).toHaveStyle({ containerType: 'inline-size' })
    expect(slot.firstElementChild).toHaveStyle({ gap: cqw(COMPACT_IDENTITY_CARD.rowGap) })
  })

  it('keeps the compact identity cell at full size below the breakpoint', () => {
    setViewport(LG_BREAKPOINT_PX - 1)

    const { container } = render(
      <CompactIdentityRow
        equipment={{ '1': { identity: { id: '10101', uptie: 4, level: 45 }, egos: {} } } as never}
        deploymentOrder={[]}
        skillDataMap={{}}
      />,
    )

    expect(root(container).firstElementChild).toHaveStyle({
      width: `${String(COMPACT_IDENTITY_GEOMETRY.size.widthPx)}px`,
    })
  })
})

/** The height the deck composite stacks up to at `widthPx`, from the parts it draws. */
function deckCompositeHeight(widthPx: number): number {
  const shares =
    DECK_CARD.padding + 2 * DECK_CARD.rowGap + DECK_CARD.skillBox + DECK_CARD.egoBox

  return widthPx / aspectOf(IDENTITY_GEOMETRY.size) + (shares / 100) * widthPx
}

function renderSinnerGrid() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <SinnerGrid
        equipment={{ '1': { identity: { id: '10101', uptie: 4, level: 45 }, egos: {} } } as never}
        deploymentOrder={[0]}
        identities={[]}
        skillDataMap={{}}
        egoAffinityMap={{}}
      />
    </QueryClientProvider>,
  )
}

describe('sinner grid tracks', () => {
  it('pins a row to the height its composite takes', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = renderSinnerGrid()

    expect(root(container)).toHaveStyle({
      gridTemplateColumns: `repeat(${String(SINNER_GRID_COLUMNS.lg)}, ${String(IDENTITY_GEOMETRY.size.widthPx)}px)`,
      gridAutoRows: `${String(deckCompositeHeight(IDENTITY_GEOMETRY.size.widthPx))}px`,
      columnGap: `${String(SINNER_GRID_GAP)}px`,
      rowGap: '0px',
    })
  })

  it('pins a row to the height its composite takes below the breakpoint', () => {
    setViewport(LG_BREAKPOINT_PX - 1)
    const columnWidth = IDENTITY_GEOMETRY.size.widthPx * CARD_MOBILE_SCALE

    const { container } = renderSinnerGrid()

    expect(root(container)).toHaveStyle({
      gridTemplateColumns: `repeat(${String(SINNER_GRID_COLUMNS.md)}, ${String(columnWidth)}px)`,
      gridAutoRows: `${String(Math.round(deckCompositeHeight(columnWidth) * 100) / 100)}px`,
      columnGap: `${String(SINNER_GRID_GAP)}px`,
      rowGap: '0px',
    })
  })
})

/**
 * A container query length on an element resolves against that element's nearest
 * *ancestor* query container: an element never queries itself. Without an ancestor
 * container the length falls back to the small viewport, so a card's transcribed share
 * of its own width becomes a share of the window.
 */
function unanchoredContainerLengths(scope: ParentNode): string[] {
  const isContainer = (el: HTMLElement) => el.style.containerType === 'inline-size'

  return Array.from(scope.querySelectorAll<HTMLElement>('[style*="cqw"]'))
    .filter((el) => {
      for (let a = el.parentElement; a !== null; a = a.parentElement) {
        if (isContainer(a)) return false
      }
      return true
    })
    .map((el) => `${el.tagName.toLowerCase()}[${el.getAttribute('style') ?? ''}]`)
}

describe('container query lengths', () => {
  it('anchors every deck card length to an ancestor container', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <SinnerGrid
          equipment={{ '1': { identity: { id: '10101', uptie: 4, level: 45 }, egos: {} } } as never}
          deploymentOrder={[0]}
          identities={[]}
          skillDataMap={{}}
          egoAffinityMap={{}}
        />
      </QueryClientProvider>,
    )

    expect(unanchoredContainerLengths(container)).toEqual([])
  })

  it('anchors every compact identity cell length to an ancestor container', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = render(
      <CompactIdentityRow
        equipment={{ '1': { identity: { id: '10101', uptie: 4, level: 45 }, egos: {} } } as never}
        deploymentOrder={[0]}
        skillDataMap={{}}
      />,
    )

    expect(unanchoredContainerLengths(container)).toEqual([])
  })

  it('anchors every sinner skill card length to an ancestor container', () => {
    setViewport(LG_BREAKPOINT_PX)

    const { container } = render(
      <CardSlot size={SINNER_SKILL_GEOMETRY.size}>
        <SinnerSkillCard
          identityId="10101"
          uptie={4}
          rank={3}
          skillInfos={[...SKILL_INFOS] as never}
          skillEA={{ 0: 3, 1: 2, 2: 1 } as never}
          onClick={() => undefined}
        />
      </CardSlot>,
    )

    expect(unanchoredContainerLengths(container)).toEqual([])
  })

  it('anchors every skill exchange length to an ancestor container', () => {
    setViewport(LG_BREAKPOINT_PX)

    render(
      <SkillExchangeModal
        open
        onOpenChange={() => undefined}
        sinnerName="Yi Sang"
        identityId="10101"
        skillInfos={[...SKILL_INFOS] as never}
        skillEA={{ 0: 3, 1: 2, 2: 1 } as never}
        onExchange={() => undefined}
        onReset={() => undefined}
      />,
    )

    expect(unanchoredContainerLengths(document.body)).toEqual([])
  })

  it('anchors every start-gift keyword icon length to an ancestor container', () => {
    const { container } = render(
      <CardSlot size={KEYWORD_ICON_GEOMETRY.size}>
        <StartGiftKeywordIcon keyword="Burn" />
      </CardSlot>,
    )

    expect(unanchoredContainerLengths(container)).toEqual([])
  })

  it('anchors every EGO gift card length to an ancestor container', () => {
    const { container } = render(
      <CardSlot size={EGO_GIFT_GEOMETRY.size}>
        <EGOGiftCard gift={{ id: '1', name: 'Gift', tag: '' } as never} />
      </CardSlot>,
    )

    expect(unanchoredContainerLengths(container)).toEqual([])
  })
})
