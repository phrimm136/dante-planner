import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render } from '@testing-library/react'
import {
  createAdvanceMeasure,
  fitFontSize,
  midlineOffsetEm,
  type FontAdvanceTable,
} from '@/shared/cardLayout'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { ThemePackEntry } from '../../types/ThemePackTypes'
import {
  THEME_PACK_LAYOUT,
  THEME_PACK_NAME_MAX_CQW,
  THEME_PACK_NAME_TEXT,
  THEME_PACK_NAME_TRACKING,
} from '../../lib/cardLayout'
import { ThemePackName } from '../ThemePackName'

/** A face carrying no glyph at all, so every character takes its average advance. */
const TABLE: FontAdvanceTable = {
  face: 'Test SDF',
  source: 'test.json',
  ascender: 1,
  descender: 0.25,
  capLine: 0.7,
  lineHeight: 1.25,
  averageAdvance: 0.5,
  advances: {},
  fallbacks: [],
}

const measure = createAdvanceMeasure(TABLE, THEME_PACK_NAME_TRACKING)

let currentLanguage = 'EN'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: currentLanguage } }),
}))

const names: Record<string, { name?: string; specialName?: string }> = {}

vi.mock('../../hooks/useThemePackListData', () => ({
  useThemePackListI18n: () => names,
}))

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => TABLE,
}))

const RECT = THEME_PACK_LAYOUT.normal.name
const MAX_CQW = THEME_PACK_NAME_MAX_CQW
const TRACK_CQW = RECT.width

const packEntry: ThemePackEntry = {
  themePackConfig: { textColor: 'AABBCC' },
  exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.HARD }],
  specificEgoGiftPool: [],
}

/**
 * The size a name is drawn at, in cqw of the card root.
 *
 * jsdom drops a `cqw` length, so the size the card prints is unreadable from the DOM;
 * the card prints what `fitFontSize` returns for this band and box.
 */
function fittedCqw(text: string): number {
  return fitFontSize(text, { max: MAX_CQW, min: 0, width: TRACK_CQW }, measure)
}

/** The one span the name renders as. */
function renderName(packId: string): HTMLSpanElement {
  const { container } = render(<ThemePackName packId={packId} packEntry={packEntry} rect={RECT} />)
  const span = container.querySelector('span')
  if (!span) throw new Error('expected the name span')
  return span
}

beforeEach(() => {
  for (const key of Object.keys(names)) delete names[key]
  currentLanguage = 'EN'
})

describe('ThemePackName', () => {
  it('draws a name that fits at the ceiling', () => {
    names.pack1 = { name: 'Ab' }

    const span = renderName('pack1')
    expect(span.textContent).toBe('Ab')
    expect(fittedCqw('Ab')).toBeCloseTo(MAX_CQW, 6)
  })

  it('shrinks a name by exactly the ratio it overruns the name box by', () => {
    const text = 'a'.repeat(40)
    names.pack1 = { name: text }

    const overrun = measure(text, MAX_CQW)
    expect(overrun).toBeGreaterThan(TRACK_CQW)
    expect(renderName('pack1').textContent).toBe(text)
    expect(fittedCqw(text)).toBeCloseTo((MAX_CQW * TRACK_CQW) / overrun, 6)
  })

  it('shrinks without a floor, however long the name is', () => {
    const text = 'a'.repeat(4000)
    names.pack1 = { name: text }

    renderName('pack1')

    const size = fittedCqw(text)
    expect(size).toBeGreaterThan(0)
    expect(size).toBeLessThan(1)
  })

  it('falls back to the pack id when the list carries no name', () => {
    const span = renderName('pack1')

    expect(span.textContent).toBe('pack1')
  })

  it('paints a plain name in the pack text color', () => {
    names.pack1 = { name: 'Ab' }

    expect(renderName('pack1').style.color).toBe('#AABBCC')
  })

  it('leaves a special name its own colors and sizes it stripped of the tags', () => {
    names.pack1 = { specialName: '<color=#FF0000>Ab</color>' }

    const span = renderName('pack1')
    expect(span.style.color).toBe('')
    expect(span.textContent).toBe('Ab')
    expect(fittedCqw('Ab')).toBeCloseTo(MAX_CQW, 6)
  })

  it('carries the language underlay as a text shadow', () => {
    names.pack1 = { name: 'Ab' }

    expect(renderName('pack1').style.textShadow).toContain('cqw')
  })

  it('carries no underlay in a language the game ships none for', () => {
    currentLanguage = 'RU'
    names.pack1 = { name: 'Ab' }

    expect(renderName('pack1').style.textShadow).toBe('')
  })

  it('carries the game tracking as em, not as card units', () => {
    names.pack1 = { name: 'Two Words' }

    const style = renderName('pack1').style
    expect(style.letterSpacing).toBe(THEME_PACK_NAME_TEXT.letterSpacing)
    expect(style.wordSpacing).toBe(THEME_PACK_NAME_TEXT.wordSpacing)
  })

  it('drops the name on to its box’s midline', () => {
    names.pack1 = { name: 'Ab' }

    expect(renderName('pack1').style.transform).toBe(
      `translateY(${String(midlineOffsetEm(TABLE) * MAX_CQW)}cqw)`,
    )
  })

  it('never wraps the name', () => {
    names.pack1 = { name: 'Two Words' }

    expect(renderName('pack1').className).toContain('whitespace-nowrap')
  })

  it('prints a source line break as a space and fits the joined line', () => {
    names.pack1 = { name: 'Two\nWords' }

    const span = renderName('pack1')
    expect(span.textContent).toBe('Two Words')
    expect(fittedCqw('Two Words')).toBeCloseTo(MAX_CQW, 6)
  })
})
