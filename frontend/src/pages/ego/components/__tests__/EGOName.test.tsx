import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import krTable from '@static/data/fontAdvances/KR.json'
import {
  createAdvanceMeasure,
  fitText,
  FontAdvanceTableSchema,
  lineMetrics,
  midlineOffsetEm,
} from '@/shared/cardLayout'
import { asEGOId } from '@/test-utils/fixtures'
import {
  EGO_NAME_CQW,
  EGO_NAME_TRACKING,
  egoNameFitSpec,
  egoNameLineHeight,
} from '../../lib/cardLayout'
import { EGOName } from '../EGOName'

/** The face the Korean cards are drawn in, as the site ships it. */
const KR_TABLE = FontAdvanceTableSchema.parse(krTable)

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => KR_TABLE,
}))

vi.mock('../../hooks/useEGOListData', () => ({
  useEGOListI18n: vi.fn(),
}))

import { useEGOListI18n } from '../../hooks/useEGOListData'

const EGO_20101 = asEGOId('20101')
const EGO_UNNAMED = asEGOId('21299')

const SPEC = egoNameFitSpec(KR_TABLE)
const measure = createAdvanceMeasure(KR_TABLE, EGO_NAME_TRACKING)

/**
 * The size a name is drawn at, in cqw of the card root.
 *
 * jsdom drops a `cqw` length, so the size the card prints is unreadable from the DOM;
 * the card prints what `fitText` returns for this spec.
 */
function fittedCqw(name: string): number {
  return fitText(name, SPEC, measure).fontSize
}

function renderName(id = EGO_20101) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <EGOName id={id} />
    </QueryClientProvider>,
  )
}

function lines(): HTMLElement[] {
  return screen.getAllByTestId('ego-name-line')
}

function lineTexts(): (string | null)[] {
  return lines().map((line) => line.textContent)
}

function nameStyle(): CSSStyleDeclaration {
  const [first] = lines()
  if (first === undefined) throw new Error('EGOName rendered no lines')
  return first.style
}

describe('EGOName', () => {
  beforeEach(() => {
    vi.mocked(useEGOListI18n).mockReturnValue({ [EGO_20101]: 'Fluid Sac' })
  })

  it('renders the name from i18n data', () => {
    renderName()

    expect(screen.getByText('Fluid Sac')).toBeInTheDocument()
  })

  it('falls back to the id when the name is missing', () => {
    renderName(EGO_UNNAMED)

    expect(screen.getByText('21299')).toBeInTheDocument()
  })

  it('keeps the top of the band for a name that fits', () => {
    expect(fittedCqw('가')).toBeCloseTo(SPEC.max, 6)
  })

  it('falls to the floor of the band for a name that never fits', () => {
    expect(fittedCqw('가'.repeat(120))).toBeCloseTo(SPEC.min, 6)
  })

  it('carries the game’s underlay, spacing and line pitch', () => {
    renderName()
    const style = nameStyle()

    expect(style.textShadow).not.toBe('')
    expect(style.letterSpacing).toBe(`${String(EGO_NAME_CQW.letterSpacingEm)}em`)
    expect(style.wordSpacing).toBe(`${String(EGO_NAME_CQW.wordSpacingEm)}em`)
    expect(style.lineHeight).toBe(String(egoNameLineHeight(lineMetrics(KR_TABLE, 'Fluid Sac'))))
  })

  it('drops the line block on to the name box’s midline', () => {
    vi.mocked(useEGOListI18n).mockReturnValue({ [EGO_20101]: '가' })

    renderName()
    const block = screen.getByTestId('ego-name')

    expect(block.style.transform).toBe(
      `translateY(${String(midlineOffsetEm(KR_TABLE) * SPEC.max)}cqw)`,
    )
  })

  it('forbids the browser re-breaking the lines it was given', () => {
    renderName()

    expect(nameStyle().whiteSpace).toBe('nowrap')
  })

  it('renders each wrapped line as its own element', () => {
    vi.mocked(useEGOListI18n).mockReturnValue({ [EGO_20101]: '눈부시지 않은 영광' })

    renderName()

    expect(lineTexts()).toEqual(['눈부시지', '않은 영광'])
  })
})

/**
 * The nine names the game's own screenshots were taken of, measured against the shipped
 * Korean advance table rather than the browser's canvas: the lines they break into and
 * the size they are drawn at, in cqw of the card root.
 */
const NAME_FIXTURES: readonly (readonly [string, string, readonly string[], number])[] = [
  ['20310', '난 가위를 낼게, 너는?', ['난 가위를', '낼게, 너는?'], EGO_NAME_CQW.maxSize],
  ['20609', '영작오 [宁作吾]', ['영작오 [宁作', '吾]'], 8.0276],
  ['21008', '오혈읍루 [汚血泣淚]', ['오혈읍루 [汚血', '泣淚]'], 8.0848],
  ['20809', '즉저살 [蝍蛆殺]', ['즉저살 [蝍蛆', '殺]'], 8.0848],
  ['20507', '갈망-미르칼라', ['갈망-', '미르칼라'], EGO_NAME_CQW.maxSize],
  ['21101', '토 파토스 마토스', ['토 파토스', '마토스'], EGO_NAME_CQW.maxSize],
  ['21001', '지식나무의 가지', ['지식나무의', '가지'], EGO_NAME_CQW.maxSize],
  ['20502', '나사빠진 일격', ['나사빠진', '일격'], EGO_NAME_CQW.maxSize],
  ['21209', '눈부시지 않은 영광', ['눈부시지', '않은 영광'], EGO_NAME_CQW.maxSize],
]

describe('EGO names on a card', () => {
  it.each(NAME_FIXTURES)(
    'breaks and sizes %s against the shipped face',
    (egoId, name, expectedLines, expectedCqw) => {
      const id = asEGOId(egoId)
      vi.mocked(useEGOListI18n).mockReturnValue({ [id]: name })

      renderName(id)

      expect(lineTexts()).toEqual([...expectedLines])
      expect(fittedCqw(name)).toBeCloseTo(expectedCqw, 4)
    },
  )
})
