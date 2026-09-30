import { afterEach, describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'

import krTable from '@static/data/fontAdvances/KR.json'
import { NAME_PLATE_UNITS } from '@/lib/constants'
import { createAdvanceMeasure, fitNodeText, FontAdvanceTableSchema } from '@/shared/cardLayout'
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

const PX_PER_UNIT = (16 * 0.875) / 40

const LONG_NAME = 'Crush the Sound, Flatten the Thought into Paste'

function nodeOf(kind: 'SKILL' | 'PASSIVE') {
  return {
    maxSize: NAME_PLATE_UNITS[kind].FONT,
    minSize: NAME_PLATE_UNITS[kind].MIN_FONT,
    letterSpacingEm: -0.02,
    wordSpacingEm: -0.05,
    lineSpacingEm: 0,
  }
}

function renderInArea(kind: 'skill' | 'passive', name: string, areaUnits: number): number {
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(
    DOMRect.fromRect({ width: areaUnits * PX_PER_UNIT, height: 0 }),
  )
  const { container } = render(<NamePlate kind={kind} name={name} attributeType="CRIMSON" />)
  const rem = Number.parseFloat(nameParts(container).name.style.fontSize)
  return (rem * 40) / 0.875
}

interface TintReading {
  filterCount: number
  matrixType: string | null
  space: string | null
  matrix: number[]
  pieces: { referencesFilter: boolean; backgroundColor: string; blend: string }[]
}

function fourPlaces(value: number): number {
  return Number(value.toFixed(4))
}

function tintReading(container: HTMLElement): TintReading {
  const filters = [...container.querySelectorAll('svg[data-slot="name-plate-tint"] filter')]
  const filter = filters[0]
  const matrix = filter?.querySelector('feColorMatrix')
  return {
    filterCount: filters.length,
    matrixType: matrix?.getAttribute('type') ?? null,
    space: filter?.getAttribute('color-interpolation-filters') ?? null,
    matrix: (matrix?.getAttribute('values') ?? '').trim().split(/\s+/).map(Number).map(fourPlaces),
    pieces: Object.values(slices(container)).map((piece) => ({
      referencesFilter: filter?.id !== undefined && piece.style.filter === `url(#${filter.id})`,
      backgroundColor: piece.style.backgroundColor,
      blend: piece.style.getPropertyValue('background-blend-mode'),
    })),
  }
}

function multiplyOnly(hex: string): TintReading {
  const [red, green, blue] = [1, 3, 5].map(
    (start) => parseInt(hex.slice(start, start + 2), 16) / 255,
  ) as [number, number, number]
  return {
    filterCount: 1,
    matrixType: 'matrix',
    space: 'sRGB',
    matrix: [red, 0, 0, 0, 0, 0, green, 0, 0, 0, 0, 0, blue, 0, 0, 0, 0, 0, 1, 0].map(fourPlaces),
    pieces: Array.from({ length: 3 }, () => ({
      referencesFilter: true,
      backgroundColor: '',
      blend: '',
    })),
  }
}

afterEach(() => {
  vi.restoreAllMocks()
})

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
    expect(name.style.lineHeight).toBe('0')
    expect(name.style.transform).toBe('')
    expect(Number.parseFloat(name.style.top)).toBeCloseTo((0.727 - 0.075) / 2, 6)
  })

  it('sets a skill name in the geometry face and drops it by its ink centre', () => {
    const { container } = render(
      <NamePlate kind="skill" name="파멸의 일격" attributeType="CRIMSON" />,
    )
    const { name } = nameParts(container)
    expect(name.style.fontFamily).toContain(' Geo')
    expect(name.style.lineHeight).toBe('0')
    expect(name.style.position).toBe('relative')
    expect(name.style.top.endsWith('em')).toBe(true)
    expect(Number.parseFloat(name.style.top)).toBeCloseTo((0.828 - 0.146) / 2, 6)
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
    expect(left.style.backgroundSize).toBe('8.1484rem 101.9608%')
    expect(left.style.backgroundPosition).toBe('left bottom')
    expect(right.style.backgroundPosition).toBe('-5.4141rem bottom')
    expect(centre.style.backgroundSize).toBe('157.672% 101.9608%')
    expect(centre.style.backgroundPosition).toBe('8.2569% bottom')
    for (const piece of [left, centre, right]) {
      expect(piece.style.height).toBe('100%')
      expect(piece.style.backgroundImage).toContain(sprite)
    }
  })

  it('maps the 25.5 skill sprite texels onto the 69.33-unit plate and the half texel outside it', () => {
    const { left, centre, right } = namePlateSliceStyles('skill', undefined)
    const texelUnits = 69.33 / 25.5
    const [, imageHeight] = String(left.backgroundSize).split(' ')
    expect((Number.parseFloat(imageHeight ?? '') / 100) * 69.33).toBeCloseTo(26 * texelUnits, 2)
    for (const piece of [left, centre, right]) {
      expect(String(piece.backgroundPosition).endsWith(' bottom')).toBe(true)
    }
    const [imageWidth] = String(left.backgroundSize).split(' ')
    expect((Number.parseFloat(imageWidth ?? '') * 40) / 0.875).toBeCloseTo(149 * 2.5, 1)
    const rightOffset = (Number.parseFloat(String(right.backgroundPosition)) * 40) / 0.875
    expect(rightOffset + 148.5 * 2.5).toBeCloseTo(123.75, 1)
  })

  it.each(['skill', 'passive'] as const)(
    'draws every %s plate piece from its sprite with no mask or blend',
    (kind) => {
      for (const piece of Object.values(namePlateSliceStyles(kind, 'plate-tint'))) {
        expect(piece.backgroundImage).toBe(`url(${getNamePlatePath(kind)})`)
        expect(piece.filter).toBe('url(#plate-tint)')
        expect(
          Object.keys(piece).filter((key) => /^mask|BlendMode$|^backgroundColor$/.test(key)),
        ).toEqual([])
      }
    },
  )

  it.each(SKILL_ATTRIBUTE_TYPES)(
    'multiplies the colour channels of every skill plate piece by the %s colour',
    (attribute) => {
      const { container } = render(<NamePlate kind="skill" name="Name" attributeType={attribute} />)
      expect(tintReading(container)).toEqual(multiplyOnly(getAttributeColors(attribute).primary))
    },
  )

  it('gives every plate on a page its own tint filter', () => {
    const { container } = render(
      <>
        <NamePlate kind="skill" name="One" attributeType="CRIMSON" />
        <NamePlate kind="skill" name="Two" attributeType="AZURE" />
      </>,
    )
    const ids = [...container.querySelectorAll('filter')].map((filter) => filter.id)
    expect(ids).toHaveLength(2)
    expect(new Set(ids).size).toBe(2)
    const pieces = container.querySelectorAll<HTMLElement>('[data-slot="name-plate-left"]')
    expect([...pieces].map((piece) => piece.style.filter)).toEqual(ids.map((id) => `url(#${id})`))
  })

  it('fits a long skill name within the client text width on a card wider than the panel', () => {
    const glyphRoom = NAME_PLATE_UNITS.SKILL.TEXT_WIDTH_MAX - 70
    const size = renderInArea('skill', LONG_NAME, 1500)
    const uncapped = fitNodeText(LONG_NAME, KR_TABLE, nodeOf('SKILL'), { width: 1430, height: 60 })
    expect(glyphRoom).toBeCloseTo(860.6, 6)
    expect(createAdvanceMeasure(KR_TABLE, nodeOf('SKILL'))(LONG_NAME, size)).toBeLessThanOrEqual(
      glyphRoom,
    )
    expect(size).toBeLessThan(uncapped.fontSize)
  })

  it('keeps the card fit for a skill name on a card narrower than the panel', () => {
    const size = renderInArea('skill', LONG_NAME, 600)
    const cardFit = fitNodeText(LONG_NAME, KR_TABLE, nodeOf('SKILL'), { width: 530, height: 60 })
    expect(size).toBeCloseTo(cardFit.fontSize, 2)
  })

  it('keeps a short skill name at its largest size on a wide card', () => {
    expect(renderInArea('skill', '소리를 부수고, 생각을 납작하게 짓이겨라', 1500)).toBeCloseTo(
      45,
      2,
    )
  })

  it('fits a long passive name within the client text width on a card wider than the panel', () => {
    const glyphRoom = NAME_PLATE_UNITS.PASSIVE.TEXT_WIDTH_MAX - 130
    const size = renderInArea('passive', LONG_NAME, 1500)
    const uncapped = fitNodeText(LONG_NAME, KR_TABLE, nodeOf('PASSIVE'), {
      width: 1370,
      height: 52.74,
    })
    expect(glyphRoom).toBeCloseTo(774.2, 6)
    expect(createAdvanceMeasure(KR_TABLE, nodeOf('PASSIVE'))(LONG_NAME, size)).toBeLessThanOrEqual(
      glyphRoom,
    )
    expect(size).toBeLessThan(uncapped.fontSize)
  })

  it('slices the white passive sprite at 112 and 171 units', () => {
    const { container } = render(<NamePlate kind="passive" name="Name" attributeType="CRIMSON" />)
    const { plate } = nameParts(container)
    const { left, centre, right } = slices(container)
    expect(plate.style.minWidth).toBe('6.1906rem')
    expect(left.style.width).toBe('2.45rem')
    expect(right.style.width).toBe('3.7406rem')
    expect(left.style.backgroundSize).toBe('8.4656rem 100%')
    expect(centre.style.backgroundSize).toBe('372.1154% 100%')
    expect(centre.style.backgroundPosition).toBe('39.576% bottom')
    for (const piece of [left, centre, right]) {
      expect(piece.style.backgroundImage).toContain(getNamePlatePath('passive'))
    }
  })

  it.each(['CRIMSON', 'NEUTRAL', undefined])(
    'tints an undecorated passive plate with the client panel brown for attribute %s',
    (attributeType) => {
      const { container } = render(
        <NamePlate kind="passive" name="Name" attributeType={attributeType} />,
      )
      expect(tintReading(container)).toEqual(multiplyOnly('#a16a3b'))
    },
  )

  it('tints a passive plate below importance level 1 with the client panel brown', () => {
    const { container } = render(
      <NamePlate kind="passive" name="Name" importance={{ level: 0, palette: 'enemy' }} />,
    )
    expect(tintReading(container)).toEqual(multiplyOnly('#a16a3b'))
  })

  it('tints the loading passive plate with the client panel brown', () => {
    const { container } = render(<NamePlateSkeleton kind="passive" attributeType="NEUTRAL" />)
    expect(tintReading(container)).toEqual(multiplyOnly('#a16a3b'))
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
    expect(tintReading(container)).toEqual(multiplyOnly('#87cb00'))
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
