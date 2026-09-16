import { describe, it, expect } from 'vitest'

import {
  breakOpportunities,
  fitFontSize,
  fitText,
  wrapText,
  type FitSpec,
  type Measure,
} from '../fitText'

/** A face whose every glyph is `WIDTH_PER_PX` wide at 1px. */
const WIDTH_PER_PX = 0.5
const measure: Measure = (text, px) => text.length * px * WIDTH_PER_PX

/** One face for every line: ascender 1, descender 0.5, and a 1.5 em line height. */
const PLAIN_METRICS = { ascender: 1, descender: 0.5, lineHeight: 1.5 }

const SPEC: FitSpec = {
  max: 20,
  min: 10,
  step: 1,
  width: 100,
  height: 60,
  lineSpacingEm: 0,
  metricsOf: () => PLAIN_METRICS,
}

/** A face twice as tall, for the lines carrying a glyph the display face has none for. */
const TALL_METRICS = { ascender: 2, descender: 0.5, lineHeight: 2.5 }
const tallOn =
  (mark: string): FitSpec['metricsOf'] =>
  (line) =>
    line.includes(mark) ? TALL_METRICS : PLAIN_METRICS

describe('breakOpportunities', () => {
  it('breaks after a space', () => {
    expect(breakOpportunities('ab cd')).toEqual([3])
  })

  it('breaks after a hyphen, which the game treats as a soft break', () => {
    expect(breakOpportunities('ab-cd')).toEqual([3])
  })

  it('ignores a hyphen that opens a word', () => {
    expect(breakOpportunities('ab -cd')).toEqual([3])
  })

  it('keeps a Hangul word whole', () => {
    expect(breakOpportunities('지식나무의')).toEqual([])
  })

  it('breaks between two Han characters', () => {
    expect(breakOpportunities('汚血泣')).toEqual([1, 2])
  })

  it('refuses a break that would end a line on a leading character', () => {
    expect(breakOpportunities('[汚血')).toEqual([2])
  })

  it('refuses a break that would open a line with a following character', () => {
    expect(breakOpportunities('汚血]')).toEqual([1])
  })

  it('breaks between kana, which the game treats as East Asian', () => {
    expect(breakOpportunities('ロボト')).toEqual([1, 2])
  })

  it('keeps a small kana with the kana it follows', () => {
    expect(breakOpportunities('チョキ')).toEqual([2])
  })

  it('keeps a prolonged sound mark with the kana it follows', () => {
    expect(breakOpportunities('エーチ')).toEqual([2])
  })

  it('breaks between an ASCII colon and the kanji after it', () => {
    expect(breakOpportunities('O::厳粛')).toEqual([3, 4])
  })

  it('refuses a break before an ASCII colon, which may not open a line', () => {
    expect(breakOpportunities('者:片')).toEqual([2])
  })

  it('breaks between kana and the Latin run after it, and back again', () => {
    expect(breakOpportunities('ーEG鳴')).toEqual([1, 3])
  })

  it('keeps a Latin word whole when no East Asian character adjoins it', () => {
    expect(breakOpportunities('E.G.O')).toEqual([])
  })

  it('keeps Hangul whole even where an ideograph adjoins it', () => {
    expect(breakOpportunities('로보토미')).toEqual([])
  })
})

describe('wrapText', () => {
  it('keeps everything on one line while it fits', () => {
    expect(wrapText('ab cd', 10, SPEC, measure).lines).toEqual(['ab cd'])
  })

  it('breaks at whitespace when the next word overruns', () => {
    const { lines, overran } = wrapText('aaaaaaaaaa bbbbbbbbbb', 20, SPEC, measure)

    expect(lines).toEqual(['aaaaaaaaaa', 'bbbbbbbbbb'])
    expect(overran).toBe(false)
  })

  it('reports a word that overruns the track on its own', () => {
    const { lines, overran } = wrapText('aaaaaaaaaaaaaaaaaaaa', 20, SPEC, measure)

    expect(lines).toEqual(['aaaaaaaaaaaaaaaaaaaa'])
    expect(overran).toBe(true)
  })

  it('takes the track from the measure, whose widths already carry tracking', () => {
    const tight: Measure = (text, px) => measure(text, px) - 0.5 * px * (text.length - 1)

    expect(wrapText('aaaaaaaaaa bbbbbbbbbb', 20, SPEC, tight).lines).toEqual([
      'aaaaaaaaaa bbbbbbbbbb',
    ])
  })
})

describe('fitText', () => {
  it('draws at the top of the band when the text already fits', () => {
    expect(fitText('aaaa', SPEC, measure)).toEqual({ fontSize: 20, lines: ['aaaa'] })
  })

  it('steps down until the wrapped text stands inside the box', () => {
    expect(fitText('aaaaa bbbbb ccccc', SPEC, measure).fontSize).toBe(18)
  })

  it('steps down until no line overruns the track on its own', () => {
    expect(fitText('a'.repeat(12), SPEC, measure).fontSize).toBe(16)
  })

  it('returns the floor rather than shrinking past the band', () => {
    expect(fitText('a'.repeat(100), SPEC, measure).fontSize).toBe(10)
  })

  it('sets a line carrying a fallback glyph from that face, not the display face', () => {
    const tall: FitSpec = { ...SPEC, height: 40, metricsOf: tallOn('汚') }

    // One line of the taller face stands 2 + 0.5 em, which overflows 40px above 16px.
    expect(fitText('汚血', tall, measure).fontSize).toBe(16)
    expect(fitText('ab', tall, measure).fontSize).toBe(20)
  })

  it('takes each line’s own pitch, not one pitch for the block', () => {
    const mixed: FitSpec = { ...SPEC, width: 30, height: 70, step: 2, metricsOf: tallOn('汚') }

    // First line ascender 2, its own pitch 2.5, last line descender 0.5: 5em fills 70px at 14px.
    expect(fitText('汚血 ab', mixed, measure).lines).toEqual(['汚血', 'ab'])
    expect(fitText('汚血 ab', mixed, measure).fontSize).toBe(14)
    // The same two lines the other way round cost the short line's pitch: 1 + 1.5 + 0.5.
    expect(fitText('ab 汚血', mixed, measure).fontSize).toBe(20)
  })

  it('stops at the first size that fits and never shrinks on to one line', () => {
    const mixed: FitSpec = { ...SPEC, width: 30, height: 70, step: 2, metricsOf: tallOn('汚') }

    expect(fitText('汚血 ab', mixed, measure).lines).toHaveLength(2)
  })
})

describe('fitFontSize', () => {
  const scale = { max: 20, min: 10, width: 100 }

  it('leaves a name that fits at the maximum', () => {
    expect(fitFontSize('aaaaa', scale, measure)).toBe(20)
  })

  it('scales a name by exactly the ratio it overruns by', () => {
    expect(fitFontSize('a'.repeat(16), scale, measure)).toBe(12.5)
  })

  it('floors a name rather than scaling it away', () => {
    expect(fitFontSize('a'.repeat(200), scale, measure)).toBe(10)
  })
})
