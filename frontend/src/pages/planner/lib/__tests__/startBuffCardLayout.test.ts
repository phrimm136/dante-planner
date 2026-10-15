import { describe, it, expect } from 'vitest'

import md7Card from '@static/data/MD7/startBuffCard.json'
import md6Card from '@static/data/MD6/startBuffCard.json'
import md7Spec from '@static/data/MD7/startBuffs.json'
import md7KrNames from '@static/i18n/KR/MD7/startBuffs.json'
import krKeywords from '@static/i18n/KR/battleKeywords.json'
import krDescriptionTable from '@static/data/fontAdvances/description/KR.json'
import krCostTable from '@static/data/fontAdvances/cost/KR.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { StartBuffDataListSchema, colorRuns, type BattleKeywords } from '@/shared/gameText'
import {
  StartBuffCardLayoutSchema,
  type StartBuffCardLayout,
  type TextElement,
} from '../../schemas/StartBuffCardSchemas'
import { toStartBuffs } from '../startBuffs'
import {
  costColor,
  fitElementText,
  fitSpace,
  layoutDescription,
  rectStyle,
  startBuffSlotSize,
  type TextRun,
} from '../startBuffCardLayout'
import { buffDescriptionText } from '../../components/startBuff/formatBuffDescription'
import capture from './__fixtures__/md7-plus2-capture-lines.json'

const MD7 = StartBuffCardLayoutSchema.parse(md7Card)
const MD6 = StartBuffCardLayoutSchema.parse(md6Card)
const KR_DESCRIPTION = FontAdvanceTableSchema.parse(krDescriptionTable)
const KR_COST = FontAdvanceTableSchema.parse(krCostTable)
const MD7_BUFFS = toStartBuffs(StartBuffDataListSchema.parse(md7Spec), md7KrNames)
const KR_KEYWORDS = Object.fromEntries(
  Object.entries(krKeywords as Record<string, { name: string; desc: string }>).map(
    ([key, entry]) => [key, { ...entry, iconId: null, buffType: 'Neutral' }],
  ),
) as BattleKeywords

function textOf(layout: StartBuffCardLayout, role: TextElement['role']): TextElement {
  const found = layout.elements.find(
    (element): element is TextElement => element.role === role && 'text' in element,
  )
  if (found === undefined) throw new Error(`${layout.prefab} has no ${role} text`)
  return found
}

const CAPTURE_LINES = capture.lines as Record<string, string[]>
const CUT_BY_VIEWPORT = ['308', '309']

function buff(id: number) {
  const found = MD7_BUFFS.find((candidate) => Number(candidate.id) === id)
  if (found === undefined) throw new Error(`MD7 has no buff ${String(id)}`)
  return found
}

function descriptionOf(id: number) {
  const text = buffDescriptionText(
    buff(id).effects,
    md7KrNames,
    MD7.description.bullet.prefix,
    KR_KEYWORDS,
  )
  return layoutDescription(colorRuns(text), MD7, KR_DESCRIPTION)
}

function lineTexts(lines: TextRun[][]): string[] {
  return lines.map((line) => line.map((run) => run.text).join(''))
}

function cqwValue(length: unknown): number {
  return Number.parseFloat(String(length))
}

describe('layoutDescription against the in-game MD7 ++ capture', () => {
  it.each(Object.keys(CAPTURE_LINES))('lays out buff %s as the game does', (id) => {
    const expected = CAPTURE_LINES[id] ?? []
    const lines = lineTexts(descriptionOf(Number(id)).lines)
    const compared = CUT_BY_VIEWPORT.includes(id) ? lines.slice(0, expected.length) : lines

    expect(compared).toEqual(expected)
  })

  it('starts the line after the last captured 308 line below the viewport', () => {
    const description = descriptionOf(308)
    const lines = lineTexts(description.lines)
    const lastVisible = lines.indexOf('• 합성 시, 합성 결과(3등급')
    const desc = MD7.desc
    const viewport = MD7.viewport
    const inkTop = (index: number) =>
      desc.rect.y -
      viewport.y +
      (description.baselines[index] ?? 0) -
      KR_DESCRIPTION.capLine * description.fontSize

    expect(lastVisible).toBe(7)
    expect(inkTop(lastVisible)).toBeLessThan(viewport.h)
    expect(inkTop(lastVisible + 1)).toBeGreaterThan(viewport.h)
    expect(description.overflows).toBe(true)
  })

  it('keeps the localized line break after the 308 gift line', () => {
    const lines = lineTexts(descriptionOf(308).lines)

    expect(lines[lines.indexOf('기프트 1개 획득') + 1]).toBe('· 5층 이하 : 2~3등급')
  })

  it('keeps the colour runs of the localized text', () => {
    const line = descriptionOf(308).lines[2] ?? []

    expect(line).toEqual([{ text: '· 5층 이하 : 2~3등급', color: '#f8c200' }])
  })

  it.each([109, 209, 309])('draws buff %i at 30 units', (id) => {
    expect(descriptionOf(id).fontSize).toBe(30)
  })
})

describe('description text model', () => {
  const effect = buff(309).effects[0]

  it('prefixes a single effect once', () => {
    if (effect === undefined) throw new Error('309 has no effect')
    const text = buffDescriptionText([effect], md7KrNames, MD7.description.bullet.prefix)

    expect(text).toBe('• <color=#dd0000>테마팩</color> 목록 +1')
    expect(text.split('•')).toHaveLength(2)
  })

  it('lays out no lines for an empty effect list', () => {
    const text = buffDescriptionText([], md7KrNames, MD7.description.bullet.prefix)

    expect(layoutDescription(colorRuns(text), MD7, KR_DESCRIPTION).lines).toEqual([])
  })
})

describe('cost', () => {
  it.each([
    [109, '#ebcaa2'],
    [209, '#f8c200'],
    [309, '#f8c200'],
  ])('colours MD7 buff %i %s', (id, color) => {
    expect(costColor(MD7, buff(id).level)).toBe(color)
  })

  it('draws 30 and 180 at the same size, 50 units', () => {
    const cost = textOf(MD7, 'cost')

    expect(fitElementText('30', cost, KR_COST)).toEqual({ fontSize: 50, lines: ['30'] })
    expect(fitElementText('180', cost, KR_COST)).toEqual({ fontSize: 50, lines: ['180'] })
  })

  it('draws every MD7 cost at the same size', () => {
    const cost = textOf(MD7, 'cost')
    const sizes = new Set(
      MD7_BUFFS.map((candidate) => fitElementText(String(candidate.cost), cost, KR_COST).fontSize),
    )

    expect([...sizes]).toEqual([50])
  })
})

describe('geometry', () => {
  function pane(layout: typeof MD7) {
    const found = layout.elements.find((element) => element.role === 'pane')
    if (found === undefined) throw new Error('no pane')
    return found
  }

  it('places the MD7 pane at its layout rect relative to the canvas', () => {
    const space = fitSpace(MD7.canvas, startBuffSlotSize(MD7.canvas))
    const style = rectStyle(pane(MD7).rect, space)
    const unit = 100 / MD7.canvas.w

    expect(pane(MD7).rect).toEqual({
      x: expect.closeTo(-9.3076, 4) as number,
      y: expect.closeTo(-26.1675, 4) as number,
      w: 450,
      h: expect.closeTo(569.143, 3) as number,
    })
    expect(cqwValue(style.left)).toBeCloseTo((-9.3076 - MD7.canvas.x) * unit, 3)
    expect(cqwValue(style.width)).toBeCloseTo(450 * unit, 3)
    expect(cqwValue(style.top)).toBeCloseTo(space.topCqw + (-26.1675 - MD7.canvas.y) * unit, 3)
  })

  it('draws the MD6 pane 481.85 units wide', () => {
    const space = fitSpace(MD6.canvas, startBuffSlotSize(MD6.canvas))

    expect(pane(MD6).rect.w).toBeCloseTo(481.85, 2)
    expect(cqwValue(rectStyle(pane(MD6).rect, space).width)).toBeCloseTo(
      (481.85 * 100) / MD6.canvas.w,
      2,
    )
  })
})
