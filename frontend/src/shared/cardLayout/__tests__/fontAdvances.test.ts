import { describe, it, expect } from 'vitest'

import krTable from '@static/data/fontAdvances/KR.json'
import {
  advanceOf,
  createAdvanceMeasure,
  FontAdvanceTableSchema,
  lineFaces,
  lineMetrics,
  midlineOffsetEm,
  type FontAdvanceTable,
} from '../fontAdvances'

/** Half-em Latin, full-em space, and no glyph for anything else. */
const TABLE: FontAdvanceTable = {
  face: 'Test SDF',
  source: 'test.json',
  ascender: 0.9,
  descender: 0.3,
  capLine: 0.7,
  lineHeight: 1.4,
  averageAdvance: 0.5,
  advances: { '97': 0.5, '98': 0.5, '32': 1 },
  fallbacks: [
    {
      face: 'Tall SDF',
      ascender: 1.2,
      descender: 0.4,
      capLine: 0.9,
      lineHeight: 1.6,
      codePoints: ['汚'.codePointAt(0) ?? 0],
    },
  ],
}

const NO_TRACKING = { letterSpacingEm: 0, wordSpacingEm: 0 }

describe('advanceOf', () => {
  it('reads the advance the face carries', () => {
    expect(advanceOf(TABLE, 'a'.codePointAt(0) ?? 0)).toBe(0.5)
  })

  it('draws an ideograph the face has no glyph for at a full em', () => {
    expect(advanceOf(TABLE, '汚'.codePointAt(0) ?? 0)).toBe(1)
  })

  it('draws any other missing code point at the table average', () => {
    expect(advanceOf(TABLE, 'Z'.codePointAt(0) ?? 0)).toBe(0.5)
  })
})

describe('createAdvanceMeasure', () => {
  it('sums the advances at the size it is asked for', () => {
    const measure = createAdvanceMeasure(TABLE, NO_TRACKING)

    expect(measure('ab', 20)).toBeCloseTo(20, 6)
    expect(measure('ab', 40)).toBeCloseTo(40, 6)
  })

  it('measures an empty string as no width at all', () => {
    expect(createAdvanceMeasure(TABLE, NO_TRACKING)('', 20)).toBe(0)
  })

  it('charges letter spacing after every glyph, as the pen advances', () => {
    const measure = createAdvanceMeasure(TABLE, { letterSpacingEm: -0.1, wordSpacingEm: 0 })

    expect(measure('ab', 10)).toBeCloseTo(10 - 2, 6)
    expect(measure('a', 10)).toBeCloseTo(5 - 1, 6)
  })

  it('charges word spacing once per space', () => {
    const measure = createAdvanceMeasure(TABLE, { letterSpacingEm: 0, wordSpacingEm: -0.5 })

    expect(measure('a b', 10)).toBeCloseTo(5 + 10 + 5 - 5, 6)
  })

  it('counts an astral code point once, not as two units', () => {
    const measure = createAdvanceMeasure(TABLE, { letterSpacingEm: -0.1, wordSpacingEm: 0 })

    expect(measure('\u{20001}', 10)).toBeCloseTo(10 - 1, 6)
  })
})

describe('lineFaces', () => {
  it('names only the faces whose glyphs the line carries', () => {
    expect(lineFaces(TABLE, 'ab').map((face) => face.ascender)).toEqual([0.9])
    expect(lineFaces(TABLE, '汚').map((face) => face.ascender)).toEqual([1.2])
    expect(lineFaces(TABLE, 'a汚').map((face) => face.ascender)).toEqual([0.9, 1.2])
  })

  it('falls to the display face for a line no face in the chain draws', () => {
    expect(lineFaces(TABLE, 'Z').map((face) => face.ascender)).toEqual([0.9])
  })
})

describe('lineMetrics', () => {
  it('lays a line out from the tallest of the faces that draw it', () => {
    expect(lineMetrics(TABLE, 'a汚')).toEqual({
      ascender: 1.2,
      descender: 0.4,
      capLine: 0.9,
      lineHeight: 1.6,
    })
  })

  it('leaves a line the display face draws alone on its own metrics', () => {
    expect(lineMetrics(TABLE, 'ab')).toEqual({
      ascender: 0.9,
      descender: 0.3,
      capLine: 0.7,
      lineHeight: 1.4,
    })
  })
})

describe('midlineOffsetEm', () => {
  it('drops the block by the gap between the leading’s middle and the glyphs’ middle', () => {
    expect(midlineOffsetEm(TABLE)).toBeCloseTo(0.7 / 2 - (0.9 - 0.3) / 2, 6)
  })

  it('leaves a face whose glyphs already centre on its leading where it stands', () => {
    expect(midlineOffsetEm({ ...TABLE, capLine: 0.6 })).toBeCloseTo(0, 6)
  })
})

describe('the shipped Korean table', () => {
  const table = FontAdvanceTableSchema.parse(krTable)

  it('is the face the KR cards are drawn in', () => {
    expect(table.face).toBe('KOTRA_BOLD SDF')
  })

  it('carries the face metrics the name block is laid out from', () => {
    expect(table.ascender).toBeCloseTo(0.927734, 6)
    expect(table.descender).toBeCloseTo(0.390625, 6)
    expect(table.capLine).toBeCloseTo(0.745098, 6)
    expect(table.lineHeight).toBeCloseTo(1.451172, 6)
  })

  it('drops the Korean name block on to the box’s midline', () => {
    expect(midlineOffsetEm(table)).toBeCloseTo(0.103995, 6)
  })

  it('names the fallback chain the face hands a hanja on to', () => {
    expect(table.fallbacks.map((fallback) => fallback.face)).toEqual([
      'Mikodacs SDF',
      'NotoSansCJKkr-Black SDF',
      'Corporate-Logo-Bold-ver2 SDF',
      'Mary_Jane_Antique SDF',
    ])
  })

  it('lays a hanja line out from the face that supplies it', () => {
    expect(lineMetrics(table, '오혈읍루 [汚血')).toEqual({
      ascender: 1.16,
      descender: 0.390625,
      capLine: 0.745098,
      lineHeight: 1.48,
    })
  })

  it('carries a Hangul syllable and no hanja, which the game falls back for', () => {
    expect(advanceOf(table, '눈'.codePointAt(0) ?? 0)).toBeCloseTo(0.986213, 6)
    expect(table.advances[String('汚'.codePointAt(0))]).toBeUndefined()
    expect(advanceOf(table, '汚'.codePointAt(0) ?? 0)).toBe(1)
  })
})
