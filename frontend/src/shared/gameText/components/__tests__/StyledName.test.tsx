import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'

import krTable from '@static/data/fontAdvances/KR.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { NamePlate, NamePlateSkeleton, StyledSkillName } from '@/shared/gameText'
import { getNamePlatePath } from '@/shared/assets'
import { SKILL_ATTRIBUTE_TYPES, getAttributeColors } from '@/shared/gameData'
import { namePlateSliceStyles } from '../../lib/namePlate'

const KR_TABLE = FontAdvanceTableSchema.parse(krTable)

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => KR_TABLE,
}))

function nameParts(container: HTMLElement): {
  plate: HTMLElement
  body: HTMLElement
  name: HTMLElement
} {
  const plate = container.querySelector<HTMLElement>('[data-slot="name-plate"]') as HTMLElement
  const body = plate.firstElementChild as HTMLElement
  return { plate, body, name: body.querySelector('span') as HTMLElement }
}

function slices(container: HTMLElement): {
  left: HTMLElement
  centre: HTMLElement
  right: HTMLElement
} {
  const piece = (name: string) =>
    container.querySelector<HTMLElement>(`[data-slot="name-plate-${name}"]`) as HTMLElement
  return { left: piece('left'), centre: piece('centre'), right: piece('right') }
}

function cssColor(color: string): string {
  const probe = document.createElement('div')
  probe.style.backgroundColor = color
  return probe.style.backgroundColor
}

describe('StyledSkillName', () => {
  it('keeps the page plate', () => {
    const { container } = render(<StyledSkillName name="Name" attributeType="NEUTRAL" />)
    const { plate, body, name } = nameParts(container)
    expect(plate.style.marginBottom).toBe('10px')
    expect(body.style.padding).toBe('0.1em 3.75em 0px 0.3em')
    expect(name.style.fontSize).toBe('20px')
  })
})

describe('NamePlate', () => {
  it('draws the passive name in the 72.74-unit plate with 30 and 100 unit margins at 40 units', () => {
    const { container } = render(<NamePlate kind="passive" name="Name" attributeType="NEUTRAL" />)
    const { body, name } = nameParts(container)
    expect(body.style.height).toBe('1.5912rem')
    expect(body.style.paddingLeft).toBe('0.6563rem')
    expect(body.style.paddingRight).toBe('2.1875rem')
    expect(name.style.fontSize).toBe('0.875rem')
  })

  it('draws the skill name in the 69.33-unit plate with 20 and 50 unit margins at 45 units', () => {
    const { container } = render(
      <NamePlate kind="skill" name="전원, 처형이다!!!" attributeType="CRIMSON" />,
    )
    const { body, name } = nameParts(container)
    expect(body.style.height).toBe('1.5166rem')
    expect(body.style.paddingLeft).toBe('0.4375rem')
    expect(body.style.paddingRight).toBe('1.0938rem')
    expect(name.style.fontSize).toBe('0.9844rem')
  })

  it('centres the skill name 5 units above the plate centre for the 10-unit bottom margin', () => {
    const { container } = render(<NamePlate kind="skill" name="Name" attributeType="NEUTRAL" />)
    const { body } = nameParts(container)
    expect(body.style.paddingTop).toBe('0rem')
    expect(body.style.paddingBottom).toBe('0.2188rem')
    expect(body.style.alignItems).toBe('center')
  })

  it('centres the passive name in a 52.74-unit box 10 units inside the plate on its midline', () => {
    const { container } = render(<NamePlate kind="passive" name="Name" attributeType="NEUTRAL" />)
    const { body, name } = nameParts(container)
    expect(body.style.paddingTop).toBe('0.2188rem')
    expect(body.style.paddingBottom).toBe('0.2188rem')
    expect(body.style.display).toBe('flex')
    expect(body.style.alignItems).toBe('center')
    expect(name.style.lineHeight).toBe('1')
    expect(name.style.transform).toMatch(/^translateY\(-?[\d.]+em\)$/)
  })

  it('draws the name with the node spacing on one line and a hard offset underlay', () => {
    const { container } = render(
      <NamePlate kind="passive" name="전원, 처형이다!!!" attributeType="CRIMSON" />,
    )
    const { body, name } = nameParts(container)
    expect(name.style.letterSpacing).toBe('-0.02em')
    expect(name.style.wordSpacing).toBe('-0.05em')
    expect(name.style.whiteSpace).toBe('nowrap')
    expect(name.style.textShadow).toMatch(/em 0(px)? /)
    expect(body.style.textShadow).toBe('')
    expect(name.textContent).toBe('전원, 처형이다!!!')
  })

  it('slices the skill sprite into fixed 11.25 and 123.75 unit ends around a stretched centre', () => {
    const { container } = render(<NamePlate kind="skill" name="Name" attributeType="CRIMSON" />)
    const { plate } = nameParts(container)
    const { left, centre, right } = slices(container)
    const sprite = getNamePlatePath('skill')
    expect(plate.style.minWidth).toBe('2.9531rem')
    expect(left.style.width).toBe('0.2461rem')
    expect(right.style.width).toBe('2.707rem')
    expect(centre.style.flexGrow).toBe('1')
    expect(left.style.flexShrink).toBe('0')
    expect(right.style.flexShrink).toBe('0')
    expect(left.style.backgroundSize).toBe('8.1211rem 100%')
    expect(left.style.backgroundPosition).toBe('left top')
    expect(right.style.backgroundPosition).toBe('right top')
    expect(centre.style.backgroundSize).toBe('157.1429% 100%')
    expect(centre.style.backgroundPosition).toBe('8.3333% top')
    for (const piece of [left, centre, right]) {
      expect(piece.style.height).toBe('100%')
      expect(piece.style.backgroundImage).toContain(sprite)
      expect(piece.style.backgroundBlendMode).toBe('multiply')
    }
  })

  it.each(['skill', 'passive'] as const)(
    'masks every %s plate piece with the same sprite piece it draws',
    (kind) => {
      for (const piece of Object.values(namePlateSliceStyles(kind, undefined))) {
        expect(piece.maskImage).toBe(`url(${getNamePlatePath(kind)})`)
        expect(piece.maskImage).toBe(piece.backgroundImage)
        expect(piece.maskSize).toBe(piece.backgroundSize)
        expect(piece.maskPosition).toBe(piece.backgroundPosition)
        expect(piece.maskRepeat).toBe('no-repeat')
      }
    },
  )

  it.each(SKILL_ATTRIBUTE_TYPES)(
    'tints every skill plate piece with the %s colour',
    (attribute) => {
      const { container } = render(<NamePlate kind="skill" name="Name" attributeType={attribute} />)
      for (const piece of Object.values(slices(container))) {
        expect(piece.style.backgroundColor).toBe(cssColor(getAttributeColors(attribute).primary))
      }
    },
  )

  it('slices the white passive sprite at 112 and 171 units and leaves it untinted', () => {
    const { container } = render(<NamePlate kind="passive" name="Name" attributeType="CRIMSON" />)
    const { plate } = nameParts(container)
    const { left, centre, right } = slices(container)
    expect(plate.style.minWidth).toBe('6.1906rem')
    expect(left.style.width).toBe('2.45rem')
    expect(right.style.width).toBe('3.7406rem')
    expect(left.style.backgroundSize).toBe('8.4656rem 100%')
    expect(centre.style.backgroundSize).toBe('372.1154% 100%')
    expect(centre.style.backgroundPosition).toBe('39.576% top')
    for (const piece of [left, centre, right]) {
      expect(piece.style.backgroundImage).toContain(getNamePlatePath('passive'))
      expect(piece.style.backgroundColor).toBe('')
      expect(piece.style.backgroundBlendMode).toBe('')
    }
  })

  it('draws no gradient on the plate', () => {
    const { container } = render(<NamePlate kind="skill" name="Name" attributeType="CRIMSON" />)
    const { plate, body } = nameParts(container)
    expect(plate.style.backgroundImage).toBe('')
    expect(body.style.backgroundImage).toBe('')
  })

  it('draws no banner without importance', () => {
    const { container } = render(
      <NamePlate kind="passive" name="Name" importance={{ level: 0, palette: 'player' }} />,
    )
    expect(container.querySelector('[data-slot="importance-banner"]')).toBeNull()
  })

  it('tints the plate of an important passive through the banner variables', () => {
    const { container } = render(
      <NamePlate kind="passive" name="Name" importance={{ level: 3, palette: 'player' }} />,
    )
    const banner = container.querySelector<HTMLElement>('[data-slot="importance-banner"]')
    expect(banner?.classList).toContain('importance-banner')
    expect(banner?.style.getPropertyValue('--name-body')).not.toBe('')
    expect(banner?.contains(nameParts(container).plate)).toBe(true)
    for (const piece of Object.values(slices(container))) {
      expect(piece.style.backgroundColor).toBe('var(--name-body)')
      expect(piece.style.backgroundBlendMode).toBe('multiply')
    }
  })
})

describe('NamePlateSkeleton', () => {
  it('sizes the loading plate as the name plate', () => {
    const { container } = render(<NamePlateSkeleton kind="passive" attributeType="NEUTRAL" />)
    const body = container.querySelector<HTMLElement>('[style*="height"]')
    expect(body?.style.height).toBe('1.5912rem')
    expect(body?.style.fontSize).toBe('0.875rem')
    expect(body?.style.paddingLeft).toBe('0.6563rem')
    expect(body?.style.paddingRight).toBe('2.1875rem')
  })
})
