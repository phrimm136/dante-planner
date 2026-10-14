import { afterEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'

import i18n from '@/lib/i18n'
import {
  getHazardGradedPlatePath,
  getHazardPlatePath,
  getLevelNumberUndefPlatePath,
} from '@/shared/assets'
import { RiskPlateSchema } from '../../../schemas/BattleDetailSchemas'
import { RiskPlate } from '../RiskPlate'

afterEach(async () => {
  await i18n.changeLanguage('EN')
})

function drawBadge(badge: unknown) {
  render(<RiskPlate plate={RiskPlateSchema.parse(badge)} />)
  return {
    plate: screen.getByTestId('battle-hazard-plate'),
    grade: screen.queryByTestId('battle-hazard-grade'),
  }
}

function boxOf(element: HTMLElement): number[] {
  const { left, top, width, height } = element.style
  return [left, top, width, height].map((value) => Number.parseFloat(value))
}

describe('risk plate badge parse', () => {
  it('parses each badge kind once at the boundary', () => {
    expect(RiskPlateSchema.parse({ classType: 'TETH', hazardGrade: '04' })).toEqual({
      kind: 'class',
      class: 'TETH',
      grade: '04',
    })
    expect(RiskPlateSchema.parse({ classType: 'SUPPLEMENT', hazardGrade: '03' })).toEqual({
      kind: 'supplement',
      grade: '03',
    })
    expect(RiskPlateSchema.parse({ classType: 'HIDDEN' })).toEqual({ kind: 'hidden' })
    expect(RiskPlateSchema.parse({ classType: 'UNDEF' })).toEqual({
      kind: 'levelNumber',
      which: 'UNDEF',
    })
    expect(RiskPlateSchema.parse({ classType: 'HYPHEN' })).toEqual({
      kind: 'levelNumber',
      which: 'HYPHEN',
    })
  })

  it('rejects a class no shipped badge carries', () => {
    expect(RiskPlateSchema.safeParse({ classType: 'CHOI' }).success).toBe(false)
  })

  it('rejects a SUPPLEMENT badge without its grade', () => {
    expect(RiskPlateSchema.safeParse({ classType: 'SUPPLEMENT' }).success).toBe(false)
  })
})

describe('RiskPlate', () => {
  it('draws a class without grade on its plain plate and no grade text', () => {
    const { plate, grade } = drawBadge({ classType: 'ZAYIN' })
    expect(plate.getAttribute('src')).toBe(getHazardPlatePath('ZAYIN'))
    expect(grade).toBeNull()
  })

  it('draws a graded ALEPH on the graded plate with its grade in red', () => {
    const { plate, grade } = drawBadge({ classType: 'ALEPH', hazardGrade: '02' })
    expect(plate.getAttribute('src')).toBe(getHazardGradedPlatePath('ALEPH'))
    expect(grade?.textContent).toBe('-02')
    expect(grade?.style.color).toBe('#ff0000')
  })

  it('places a class grade in the [Text]HazardGrade rect of the plate', () => {
    const { grade } = drawBadge({ classType: 'TETH', hazardGrade: '04' })
    if (grade === null) throw new Error('no grade')
    const [left, top, width, height] = boxOf(grade)
    expect(left).toBeCloseTo(73.8777, 3)
    expect(top).toBeCloseTo(7.7162, 3)
    expect(width).toBeCloseTo(26.5308, 3)
    expect(height).toBeCloseTo(49.7457, 3)
    expect(grade.style.fontFamily).toBe('var(--font-numerals-ink)')
  })

  it('places a SUPPLEMENT grade in the [Text]HazardGrade_NoneClassType rect', () => {
    const { grade } = drawBadge({ classType: 'SUPPLEMENT', hazardGrade: '03' })
    if (grade === null) throw new Error('no grade')
    expect(grade.textContent).toBe('-03')
    expect(grade.style.color).toBe('#f8c200')
    const [left, top, width, height] = boxOf(grade)
    expect(left).toBeCloseTo(63.5307, 3)
    expect(top).toBeCloseTo(5.8507, 3)
    expect(width).toBeCloseTo(34.4901, 3)
    expect(height).toBeCloseTo(68.4003, 3)
  })

  it.each([
    ['JP', 'jp'],
    ['CN', 'en'],
  ] as const)('draws the UNDEF plate on a %s page as the %s plate', async (language, plate) => {
    await i18n.changeLanguage(language)
    expect(drawBadge({ classType: 'UNDEF' }).plate.getAttribute('src')).toBe(
      getLevelNumberUndefPlatePath(plate),
    )
  })
})
