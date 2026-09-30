import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import enemy1339 from '@static/data/enemy/1339.json'
import { getAbPartIconPath } from '@/shared/assets'
import { SkillEntrySchema, type AbPartType } from '@/shared/gameData'
import { panelNameShadow } from '@/shared/gameText'
import { toSkillCardData } from '../../lib/skillLevel'
import { SkillCard } from '../SkillCard'

const [phase] = enemy1339.units['1339'].phases
const entry = SkillEntrySchema.parse(phase?.skills['133907'])

interface CardOptions {
  partTypes?: readonly AbPartType[]
  importancePosition?: 'unlock' | 'base'
  isLocked?: boolean
  importanceLevel?: number
  targetNum?: number
}

function renderCard({
  partTypes,
  importancePosition = 'base',
  isLocked = false,
  importanceLevel = 0,
  targetNum,
}: CardOptions) {
  const [level] = entry.levels
  const data = toSkillCardData(entry, level)
  return render(
    <SkillCard
      skillId="133907"
      skillData={targetNum === undefined ? data : { ...data, targetNum }}
      coins={level.coins}
      skillImagePath={undefined}
      skillTier={entry.skillTier}
      nameSlot={<span>Name</span>}
      descriptionSlot={<span>Desc</span>}
      unitLevel={60}
      importanceLevel={importanceLevel}
      palette="enemy"
      importancePosition={importancePosition}
      partTypes={partTypes}
      isLocked={isLocked}
    />,
  )
}

function partIcons(container: HTMLElement): HTMLElement[] {
  return [...container.querySelectorAll<HTMLElement>('[data-slot="skill-part-icon"]')]
}

function box(container: HTMLElement): HTMLElement {
  const layout = container.querySelector<HTMLElement>('[data-skill-id]')
  if (layout?.parentElement == null) throw new Error('no card box')
  return layout.parentElement
}

function triangleRight(container: HTMLElement): string {
  const decoration = container.querySelector<HTMLElement>('[data-slot="importance-decoration"]')
  if (decoration === null) throw new Error('no importance decoration')
  return decoration.style.getPropertyValue('--importance-icon-right')
}

describe('skill part-icon strip visibility', () => {
  it('draws one head icon for an unlocked whole-unit skill linked to HEAD', () => {
    const { container } = renderCard({ partTypes: ['HEAD'] })
    const icons = partIcons(container)
    expect(icons).toHaveLength(1)
    expect(icons[0]?.style.maskImage).toBe(`url(${getAbPartIconPath('HEAD')})`)
  })

  it.each<[string, CardOptions]>([
    ['a part view', { partTypes: ['HEAD'], importancePosition: 'unlock' }],
    ['a locked skill', { partTypes: ['HEAD'], isLocked: true }],
    ['no linked part types', {}],
    ['an empty part list', { partTypes: [] }],
  ])('draws no icon for %s', (_name, options) => {
    const { container } = renderCard(options)
    expect(partIcons(container)).toEqual([])
    expect(box(container).classList.contains('isolate')).toBe(false)
  })
})

describe('skill part-icon strip placement', () => {
  it('puts slots 0-2 down the right column and slot 3 at the top of the left column', () => {
    const { container } = renderCard({ partTypes: ['HEAD', 'BODY', 'LEG', 'TAIL'] })
    const icons = partIcons(container)
    expect(icons.map((icon) => [icon.style.right, icon.style.top])).toEqual([
      ['1.5313rem', '0rem'],
      ['1.5313rem', '4.375rem'],
      ['1.5313rem', '8.75rem'],
      ['5.9063rem', '0rem'],
    ])
    for (const icon of icons) {
      expect(icon.style.width).toBe('5.6875rem')
      expect(icon.style.height).toBe('5.6875rem')
      expect(icon.style.opacity).toBe('0.275')
      expect(icon.style.backgroundColor).toBe('#ebcaa2')
      expect(icon.style.maskSize).toBe('100% 100%')
    }
  })

  it('draws at most five icons', () => {
    const { container } = renderCard({
      partTypes: ['HEAD', 'BODY', 'LEG', 'TAIL', 'BACK', 'LEFT_ARM'],
    })
    expect(partIcons(container)).toHaveLength(5)
  })

  it('draws duplicate part types as given', () => {
    const { container } = renderCard({ partTypes: ['HEAD', 'HEAD'] })
    expect(partIcons(container).map((icon) => icon.dataset.partType)).toEqual(['HEAD', 'HEAD'])
  })

  it('stacks the icons after the importance decoration, below the card content', () => {
    const { container } = renderCard({ partTypes: ['HEAD'], importanceLevel: 2 })
    const wrapper = box(container)
    expect(wrapper.classList.contains('isolate')).toBe(true)
    const [icon] = partIcons(container)
    const decoration = container.querySelector('[data-slot="importance-decoration"]')
    expect(icon?.classList.contains('-z-10')).toBe(true)
    expect(icon?.parentElement).toBe(wrapper)
    expect(decoration?.compareDocumentPosition(icon as Node)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
  })
})

describe('skill box floor under the part icons', () => {
  it.each<[AbPartType[], string]>([
    [['HEAD'], '5.6875rem'],
    [['HEAD', 'BODY'], '10.0625rem'],
    [['HEAD', 'BODY', 'LEG'], '14.4375rem'],
  ])('floors the box for %j at %s', (partTypes, minHeight) => {
    const { container } = renderCard({ partTypes })
    expect(box(container).style.minHeight).toBe(minHeight)
  })

  it('sets no floor without icons', () => {
    const { container } = renderCard({
      partTypes: ['HEAD'],
      importancePosition: 'unlock',
      importanceLevel: 2,
    })
    expect(box(container).style.minHeight).toBe('')
  })
})

describe('importance triangle beside the part icons', () => {
  it.each<[AbPartType[], string]>([
    [['HEAD'], '6.2562rem'],
    [['HEAD', 'BODY', 'LEG'], '6.2562rem'],
    [['HEAD', 'BODY', 'LEG', 'TAIL'], '10.6312rem'],
    [['HEAD', 'BODY', 'LEG', 'TAIL', 'BACK', 'LEFT_ARM'], '10.6312rem'],
  ])('insets the triangle for %j by %s', (partTypes, right) => {
    const { container } = renderCard({ partTypes, importanceLevel: 2 })
    expect(triangleRight(container)).toBe(right)
  })
})

describe('target count glyph', () => {
  it('draws the attack weight as one KOTRA text node of squares with the name underlay', () => {
    const { container } = renderCard({ targetNum: 3 })
    const count = container.querySelector<HTMLElement>('[data-slot="skill-target-count"]')
    expect(count?.textContent).toBe('■■■')
    expect(count?.children).toHaveLength(0)
    expect(count?.style.fontFamily).toBe('var(--font-kotra)')
    expect(count?.classList.contains('font-bold')).toBe(true)
    expect(count?.style.fontSize).toBe('0.8218rem')
    expect(count?.style.letterSpacing).toBe('0.05em')
    expect(count?.style.lineHeight).toBe('1.318359')
    const probe = document.createElement('span')
    probe.style.textShadow = String(panelNameShadow('KR'))
    expect(probe.style.textShadow).not.toBe('')
    expect(count?.style.textShadow).toBe(probe.style.textShadow)
    expect(container.querySelector('[data-slot="skill-target-count"] img')).toBeNull()
  })
})
