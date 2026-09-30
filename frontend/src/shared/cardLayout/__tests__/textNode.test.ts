import { describe, expect, it } from 'vitest'

import krTable from '@static/data/fontAdvances/KR.json'
import enTable from '@static/data/fontAdvances/EN.json'
import jpTable from '@static/data/fontAdvances/JP.json'
import cnTable from '@static/data/fontAdvances/CN.json'
import { createAdvanceMeasure, FontAdvanceTableSchema } from '../fontAdvances'
import { fitNodeLine, fitNodeText, type TextNodeSpec } from '../textNode'

const TABLES = {
  KR: FontAdvanceTableSchema.parse(krTable),
  EN: FontAdvanceTableSchema.parse(enTable),
  JP: FontAdvanceTableSchema.parse(jpTable),
  CN: FontAdvanceTableSchema.parse(cnTable),
}

const PANIC_NAME: TextNodeSpec = {
  maxSize: 55,
  minSize: 18,
  letterSpacingEm: -0.03,
  wordSpacingEm: -0.05,
  lineSpacingEm: -0.15,
}

const PANIC_NAME_BOX = { width: 235, height: 100.274 }

const EMULATED: readonly (readonly [keyof typeof TABLES, string, number, string[]])[] = [
  ['KR', '사랑과 증오', 46.7, ['사랑과 증오']],
  ['KR', '저택의 메아리', 39.35, ['저택의 메아리']],
  ['KR', '깨어나는 살육의 고양감', 38.25, ['깨어나는', '살육의 고양감']],
  ['KR', '돌이킬 수 없이 망가진 몸', 38.1, ['돌이킬 수 없이', '망가진 몸']],
  ['EN', 'Love and Hate', 51.65, ['Love and', 'Hate']],
  ['EN', 'Compulsion', 47.45, ['Compulsion']],
  [
    'EN',
    'Infinitely Bequeathing Will of Hongyuan',
    32.3,
    ['Infinitely', 'Bequeathing Will', 'of Hongyuan'],
  ],
  ['JP', '愛と憎悪', 55, ['愛と憎悪']],
  ['JP', '邸宅の木霊', 49.85, ['邸宅の木霊']],
  ['CN', '山庄的回响', 48.15, ['山庄的回响']],
]

describe('fitNodeText on the panic name node', () => {
  it.each(EMULATED)('fits %s %s at the emulated size and lines', (language, name, size, lines) => {
    const fitted = fitNodeText(name, TABLES[language], PANIC_NAME, PANIC_NAME_BOX)

    expect(Math.abs(fitted.fontSize - size)).toBeLessThanOrEqual(0.1)
    expect(fitted.lines).toEqual(lines)
  })
})

describe('fitNodeLine', () => {
  const TAG: TextNodeSpec = {
    maxSize: 40,
    minSize: 18,
    letterSpacingEm: -0.02,
    wordSpacingEm: -0.05,
    lineSpacingEm: 0,
  }

  it('keeps the top of the band when the line fits', () => {
    expect(fitNodeLine('조건', TABLES.KR, TAG, 525)).toBe(40)
  })

  it('steps down to the first size whose line fits the width', () => {
    const text = '정신력이 증가하는 조건이 길게 이어지는 태그'
    const size = fitNodeLine(text, TABLES.KR, TAG, 525)
    const measure = createAdvanceMeasure(TABLES.KR, TAG)

    expect(size).toBeLessThan(40)
    expect(measure(text, size)).toBeLessThanOrEqual(525)
    expect(measure(text, size + 0.05)).toBeGreaterThan(525)
  })
})
