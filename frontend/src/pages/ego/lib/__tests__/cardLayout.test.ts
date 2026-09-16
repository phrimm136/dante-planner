import { describe, it, expect } from 'vitest'

import jpTable from '@static/data/fontAdvances/JP.json'
import krTable from '@static/data/fontAdvances/KR.json'
import {
  createAdvanceMeasure,
  fitText,
  FontAdvanceTableSchema,
  lineMetrics,
  midlineOffsetEm,
} from '@/shared/cardLayout'
import {
  EGO_CARD_LAYERS,
  EGO_NAME_CQW,
  EGO_NAME_RECT,
  EGO_NAME_TRACKING,
  egoNameFitSpec,
  egoNameLineHeight,
} from '../cardLayout'

/** The face the Korean cards are drawn in, as the site ships it. */
const KR_TABLE = FontAdvanceTableSchema.parse(krTable)

/** The face the Japanese cards are drawn in, as the site ships it. */
const JP_TABLE = FontAdvanceTableSchema.parse(jpTable)

describe('egoNameFitSpec', () => {
  it('carries the name band in cqw of the card root', () => {
    const spec = egoNameFitSpec(KR_TABLE)

    expect(spec.max).toBeCloseTo(8.571, 4)
    expect(spec.min).toBeCloseTo(7.143, 4)
    expect(spec.step).toBeCloseTo(0.0143, 5)
  })

  it('carries the name box height, in cqw, as well as its width', () => {
    expect(egoNameFitSpec(KR_TABLE).height).toBeCloseTo(22.8892, 3)
  })

  it('measures against the name box, not the whole card', () => {
    expect(egoNameFitSpec(KR_TABLE).width).toBeCloseTo(51.429, 4)
  })

  it('keeps the band ordered', () => {
    const spec = egoNameFitSpec(KR_TABLE)

    expect(spec.max).toBeGreaterThan(spec.min)
    expect(EGO_NAME_CQW.letterSpacingEm).toBe(-0.02)
    expect(EGO_NAME_CQW.wordSpacingEm).toBe(-0.05)
  })
})

describe('EGO_CARD_LAYERS', () => {
  it('places the portrait window inside the card and the frame outside it', () => {
    expect(EGO_CARD_LAYERS.portraitWindow.rect).toEqual({
      left: 0.857,
      top: 0.667,
      width: 98.286,
      height: 98.667,
    })
    expect(EGO_CARD_LAYERS.frame.rect.left).toBeLessThan(0)
    expect(EGO_CARD_LAYERS.hoverRing.rect.left).toBeLessThan(EGO_CARD_LAYERS.frame.rect.left)
  })

  it('centres the sinner icon ring and its face on the card', () => {
    const ring = EGO_CARD_LAYERS.iconRing.rect
    const face = EGO_CARD_LAYERS.face.rect

    expect(ring.left + ring.width / 2).toBeCloseTo(50, 2)
    expect(face.left + face.width / 2).toBeCloseTo(50, 2)
    expect(ring.top).toBe(0)
  })

  it('centres the name box on the card', () => {
    const name = EGO_NAME_RECT

    expect(name.left + name.width / 2).toBeCloseTo(50, 2)
  })

  it('hangs the name plate over both edges', () => {
    const plate = EGO_CARD_LAYERS.namePlate.rect

    expect(plate.left).toBeLessThan(0)
    expect(plate.left + plate.width).toBeGreaterThan(100)
  })
})

const egoMeasure = createAdvanceMeasure(KR_TABLE, EGO_NAME_TRACKING)

function egoLines(name: string): string[] {
  return fitText(name, egoNameFitSpec(KR_TABLE), egoMeasure).lines
}

describe('the name block’s line metrics', () => {
  it('carries the node’s own line spacing, untouched by the face', () => {
    expect(egoNameFitSpec(KR_TABLE).lineSpacingEm).toBe(EGO_NAME_CQW.lineSpacing)
  })

  it('reads a line’s metrics off the faces that draw it', () => {
    const spec = egoNameFitSpec(KR_TABLE)

    expect(spec.metricsOf('눈부시지').ascender).toBeCloseTo(KR_TABLE.ascender, 6)
    expect(spec.metricsOf('泣淚]').ascender).toBeCloseTo(1.16, 6)
  })

  it('pitches a Hangul line at the face line height plus the node’s line spacing', () => {
    expect(egoNameLineHeight(lineMetrics(KR_TABLE, '눈부시지'))).toBeCloseTo(1.251172, 6)
  })

  it('pitches a hanja line at the fallback face’s taller line height', () => {
    expect(egoNameLineHeight(lineMetrics(KR_TABLE, '泣淚]'))).toBeCloseTo(1.28, 6)
  })
})

describe('fitText over the game’s EGO names', () => {
  it.each([
    ['20310', '난 가위를 낼게, 너는?', ['난 가위를', '낼게, 너는?']],
    ['20507', '갈망-미르칼라', ['갈망-', '미르칼라']],
    ['21008', '오혈읍루 [汚血泣淚]', ['오혈읍루 [汚血', '泣淚]']],
    ['20809', '즉저살 [蝍蛆殺]', ['즉저살 [蝍蛆', '殺]']],
    ['20609', '영작오 [宁作吾]', ['영작오 [宁作', '吾]']],
    ['21101', '토 파토스 마토스', ['토 파토스', '마토스']],
    ['21001', '지식나무의 가지', ['지식나무의', '가지']],
    ['20502', '나사빠진 일격', ['나사빠진', '일격']],
    ['21209', '눈부시지 않은 영광', ['눈부시지', '않은 영광']],
  ])('breaks %s as the game does', (_id, name, expected) => {
    expect(egoLines(name)).toEqual(expected)
  })

  it('stops at the first size a hanja name’s two lines fit, never shrinking to one', () => {
    const spec = egoNameFitSpec(KR_TABLE)
    const fitted = fitText('영작오 [宁作吾]', spec, egoMeasure)

    expect(fitted.lines).toHaveLength(2)
    expect(fitted.fontSize).toBeCloseTo(8.0276, 4)
    expect(fitted.fontSize).toBeLessThan(spec.max)
  })
})

const jpMeasure = createAdvanceMeasure(JP_TABLE, EGO_NAME_TRACKING)

function egoJapaneseLines(name: string): string[] {
  return fitText(name, egoNameFitSpec(JP_TABLE), jpMeasure).lines
}

describe('fitText over the game’s Japanese EGO names', () => {
  it.each([
    ['20301', 'ラ・サングレ・デ・サンチョ', ['ラ・サングレ・', 'デ・サンチョ']],
    ['20309', '愛と憎悪の名の下に', ['愛と憎悪の名', 'の下に']],
    ['20310', '私はチョキを出すね、そっちは？', ['私はチョキを出', 'すね、そっちは？']],
    ['20307', '渇望-ミルカラ', ['渇望-ミルカラ']],
    ['11006', '紅籠', ['紅籠']],
    ['20103', '願いの石', ['願いの石']],
    ['20306', '電気哀鳴', ['電気哀鳴']],
    ['20204', '電信柱', ['電信柱']],
  ])('breaks %s as the game does', (_id, name, expected) => {
    expect(egoJapaneseLines(name)).toEqual(expected)
  })

  it('never opens a line on a middle dot or an ideographic comma', () => {
    for (const name of ['ラ・サングレ・デ・サンチョ', '私はチョキを出すね、そっちは？']) {
      for (const line of egoJapaneseLines(name).slice(1)) {
        expect(['・', '、']).not.toContain(line[0])
      }
    }
  })

  it('draws every one of these names at the top of the band', () => {
    const spec = egoNameFitSpec(JP_TABLE)

    expect(fitText('愛と憎悪の名の下に', spec, jpMeasure).fontSize).toBe(spec.max)
  })

  it('offsets a Japanese line block by its own face’s midline, not the Korean one', () => {
    expect(midlineOffsetEm(JP_TABLE)).toBeCloseTo(-0.027059, 6)
    expect(midlineOffsetEm(KR_TABLE)).toBeCloseTo(0.1039945, 6)
  })
})
