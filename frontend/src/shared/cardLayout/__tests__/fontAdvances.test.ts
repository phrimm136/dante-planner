import { describe, it, expect } from 'vitest'

import krTable from '@static/data/fontAdvances/KR.json'
import enTable from '@static/data/fontAdvances/EN.json'
import {
  advanceOf,
  blockGeometryCentreEm,
  createAdvanceMeasure,
  faceTracking,
  FontAdvanceTableSchema,
  geometryCentreEm,
  lineFaces,
  lineMetrics,
  linePitchesEm,
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
  normalSpacingOffset: 0,
  averageAdvance: 0.5,
  advances: { '97': 0.5, '98': 0.5, '32': 1 },
  inkTop: [0, 500, 700],
  inkBottom: [0, -200, 0],
  fallbacks: [
    {
      face: 'Tall SDF',
      ascender: 1.2,
      descender: 0.4,
      capLine: 0.9,
      lineHeight: 1.6,
      normalSpacingOffset: -0.2,
      codePoints: ['汚'.codePointAt(0) ?? 0],
      inkTop: [800],
      inkBottom: [-100],
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

  it('charges letter spacing between glyphs and not after the last', () => {
    const measure = createAdvanceMeasure(TABLE, { letterSpacingEm: -0.1, wordSpacingEm: 0 })

    expect(measure('ab', 10)).toBeCloseTo(10 - 1, 6)
    expect(measure('a', 10)).toBeCloseTo(5, 6)
  })

  it('charges word spacing once per space', () => {
    const measure = createAdvanceMeasure(TABLE, { letterSpacingEm: 0, wordSpacingEm: -0.5 })

    expect(measure('a b', 10)).toBeCloseTo(5 + 10 + 5 - 5, 6)
  })

  it('counts an astral code point once, not as two units', () => {
    const measure = createAdvanceMeasure(TABLE, { letterSpacingEm: -0.1, wordSpacingEm: 0 })

    expect(measure('\u{20001}\u{20001}', 10)).toBeCloseTo(20 - 1, 6)
  })

  it('charges the spacing offset of the face that draws each glyph', () => {
    const measure = createAdvanceMeasure(TABLE, NO_TRACKING)

    expect(measure('a汚', 10)).toBeCloseTo(5 + 10, 6)
    expect(measure('汚a', 10)).toBeCloseTo(10 - 2 + 5, 6)
  })
})

describe('faceTracking', () => {
  it('adds the display face spacing offset to the node character spacing', () => {
    const table = { ...TABLE, normalSpacingOffset: 0.06 }

    expect(faceTracking(table, { letterSpacingEm: -0.03, wordSpacingEm: -0.05 })).toEqual({
      letterSpacingEm: -0.03 + 0.06,
      wordSpacingEm: -0.05,
    })
  })

  it('draws an overridden glyph at the advance it is handed', () => {
    const measure = createAdvanceMeasure(TABLE, NO_TRACKING, new Map([['•', 0.51]]))

    expect(measure('•a', 10)).toBeCloseTo(5.1 + 5, 6)
  })
})

describe('FontAdvanceTableSchema', () => {
  it('keeps the sign of a descent line above the baseline', () => {
    expect(FontAdvanceTableSchema.parse({ ...TABLE, descender: -0.24 }).descender).toBe(-0.24)
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
    expect(lineMetrics(TABLE, 'a汚')).toMatchObject({
      ascender: 1.2,
      descender: 0.4,
      lineHeight: 1.6,
    })
  })

  it('keeps the display face’s line gap on a line a fallback face draws', () => {
    expect(lineMetrics(TABLE, 'a汚').lineGap).toBeCloseTo(1.4 - 0.9 - 0.3, 6)
  })

  it('leaves a line the display face draws alone on its own metrics', () => {
    expect(lineMetrics(TABLE, 'ab')).toMatchObject({
      ascender: 0.9,
      descender: 0.3,
      lineHeight: 1.4,
    })
  })

  it('keeps the signed descender of a face whose descent line sits above the baseline', () => {
    const metrics = lineMetrics({ ...TABLE, ascender: 0.76, descender: -0.24, fallbacks: [] }, '12')

    expect(metrics.descender).toBe(-0.24)
    expect(metrics.ascender + metrics.descender).toBeCloseTo(0.52, 6)
  })
})

describe('geometryCentreEm', () => {
  it('drops a line by the middle of its visible glyphs’ ink union', () => {
    expect(geometryCentreEm(TABLE, 'ab')).toBeCloseTo((0.7 - 0.2) / 2, 6)
  })

  it('ignores spaces, zero width spaces, soft hyphens and code points it has no ink for', () => {
    expect(geometryCentreEm(TABLE, ' a\u200bb\u00ad Z\u3000')).toBeCloseTo((0.7 - 0.2) / 2, 6)
  })

  it('reads a fallback face’s glyph from that face’s ink', () => {
    expect(geometryCentreEm(TABLE, 'a汚')).toBeCloseTo((0.8 - 0.2) / 2, 6)
  })

  it('leaves an empty or whitespace-only line where it stands', () => {
    expect(geometryCentreEm(TABLE, '')).toBe(0)
    expect(geometryCentreEm(TABLE, ' \u200b ')).toBe(0)
  })
})

describe('linePitchesEm', () => {
  it('drops each line below the first by the TextMeshPro pitch', () => {
    expect(linePitchesEm(TABLE, ['a', 'b'], 0)).toEqual([0, expect.closeTo(0.3 + 0.9 + 0.2, 6)])
    expect(linePitchesEm(TABLE, ['a', 'b'], -0.2)).toEqual([0, expect.closeTo(1.2, 6)])
  })
})

describe('blockGeometryCentreEm', () => {
  it('drops a single line as geometryCentreEm does', () => {
    expect(blockGeometryCentreEm(TABLE, ['ab'], 0)).toBeCloseTo(geometryCentreEm(TABLE, 'ab'), 6)
  })

  it('centres the union of every line’s ink, each line dropped by its pitch', () => {
    expect(blockGeometryCentreEm(TABLE, ['a', 'b'], 0)).toBeCloseTo((0.5 + 0) / 2, 6)
    expect(blockGeometryCentreEm(TABLE, ['b', 'a'], -0.2)).toBeCloseTo((0.7 - 0.2) / 2, 6)
  })

  it('leaves a block with no ink where it stands', () => {
    expect(blockGeometryCentreEm(TABLE, [' ', ' '], 0)).toBe(0)
  })
})

describe('FontAdvanceTableSchema', () => {
  it('rejects ink edges that do not align with the advances', () => {
    expect(() => FontAdvanceTableSchema.parse({ ...TABLE, inkTop: [0, 500] })).toThrow(
      'ink edges must align with the advances',
    )
  })

  it('rejects fallback ink edges that do not align with its code points', () => {
    const fallbacks = TABLE.fallbacks.map((fallback) => ({ ...fallback, inkBottom: [] }))
    expect(() => FontAdvanceTableSchema.parse({ ...TABLE, fallbacks })).toThrow(
      'ink edges must align with the fallback code points',
    )
  })
})

describe('the shipped spacing offsets', () => {
  it('carries the Mikodacs face offset of +6 hundredths of an em', () => {
    const table = FontAdvanceTableSchema.parse(enTable)

    expect(table.face).toBe('Mikodacs SDF')
    expect(table.normalSpacingOffset).toBeCloseTo(0.06, 6)
  })

  it('carries each Korean fallback face its own offset', () => {
    const table = FontAdvanceTableSchema.parse(krTable)

    expect(table.normalSpacingOffset).toBe(0)
    expect(table.fallbacks.map((fallback) => fallback.normalSpacingOffset)).toEqual([
      0.06, 0, 0, -0.15,
    ])
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

  it('centres a Hangul name on the ink of its tallest and deepest glyphs', () => {
    expect(geometryCentreEm(table, '파멸의 일격')).toBeCloseTo((0.828 - 0.146) / 2, 6)
    expect(geometryCentreEm(table, '파멸의\u200b 일격 ')).toBeCloseTo((0.828 - 0.146) / 2, 6)
  })

  it('centres a Latin line on its own ink, not half the cap line', () => {
    const centre = geometryCentreEm(table, 'ABCdefg')

    expect(centre).toBeCloseTo((0.763 - 0.245) / 2, 6)
    expect(Math.abs(centre - table.capLine / 2)).toBeGreaterThanOrEqual(0.05)
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
    expect(lineMetrics(table, '오혈읍루 [汚血')).toMatchObject({
      ascender: 1.16,
      descender: 0.390625,
      lineHeight: 1.48,
    })
  })

  it('gaps a hanja line by KOTRA’s own line gap, not the fallback face’s', () => {
    expect(lineMetrics(table, '오혈읍루 [汚血').lineGap).toBeCloseTo(0.132813, 6)
  })

  it('carries a Hangul syllable and no hanja, which the game falls back for', () => {
    expect(advanceOf(table, '눈'.codePointAt(0) ?? 0)).toBeCloseTo(0.986213, 6)
    expect(table.advances[String('汚'.codePointAt(0))]).toBeUndefined()
    expect(advanceOf(table, '汚'.codePointAt(0) ?? 0)).toBe(1)
  })
})
