import { describe, it, expect, vi, afterEach } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen, fireEvent, within } from '@testing-library/react'

import md7Card from '@static/data/MD7/startBuffCard.json'
import md7Spec from '@static/data/MD7/startBuffs.json'
import md7KrNames from '@static/i18n/KR/MD7/startBuffs.json'
import krNameTable from '@static/data/fontAdvances/KR.json'
import krDescriptionTable from '@static/data/fontAdvances/description/KR.json'
import krCostTable from '@static/data/fontAdvances/cost/KR.json'
import { FontAdvanceTableSchema, type FontTableRole } from '@/shared/cardLayout'
import { StartBuffDataListSchema, type EnhancementLevel } from '@/shared/gameText'
import { StartBuffCardLayoutSchema } from '../../../schemas/StartBuffCardSchemas'
import { startBuffSlotSize } from '../../../lib/startBuffCardLayout'
import { toStartBuffs, getBaseBuffs } from '../../../lib/startBuffs'
import { getStartBuffSpritePath } from '@/shared/assets'
import { StartBuffCard } from '../StartBuffCard'

const KR_TABLES: Record<FontTableRole, ReturnType<typeof FontAdvanceTableSchema.parse>> = {
  name: FontAdvanceTableSchema.parse(krNameTable),
  description: FontAdvanceTableSchema.parse(krDescriptionTable),
  cost: FontAdvanceTableSchema.parse(krCostTable),
}

const textTables = vi.hoisted(() => ({ pending: null as Promise<never> | null }))

vi.mock('@/shared/cardLayout', async (importOriginal) => {
  const { use } = await import('react')
  return {
    ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
    useFontAdvances: (_language: string, role: FontTableRole = 'name') => {
      if (textTables.pending !== null) use(textTables.pending)
      return KR_TABLES[role]
    },
  }
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'KR' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const MD7 = StartBuffCardLayoutSchema.parse(md7Card)
const MD7_BUFFS = toStartBuffs(StartBuffDataListSchema.parse(md7Spec), md7KrNames)

function baseBuff(baseId: number) {
  const found = getBaseBuffs(MD7_BUFFS).find((candidate) => candidate.baseId === baseId)
  if (found === undefined) throw new Error(`MD7 has no base buff ${String(baseId)}`)
  return found
}

function layoutFiles(): Set<string> {
  const files = new Set<string>()
  for (const element of MD7.elements) {
    if (!('image' in element)) continue
    if (element.image.file !== null) files.add(element.image.file)
    if (element.image.filePattern !== undefined) {
      files.add(element.image.filePattern.replace('{baseId}', '109'))
    }
  }
  return files
}

function renderCard(
  overrides: Partial<Parameters<typeof StartBuffCard>[0]> & { baseId?: number } = {},
) {
  const { baseId = 109, ...rest } = overrides
  const props = {
    mdVersion: 7,
    layout: MD7,
    slot: startBuffSlotSize(MD7.canvas),
    buff: baseBuff(baseId),
    allBuffs: MD7_BUFFS,
    i18n: md7KrNames,
    isSelected: false,
    onSelect: vi.fn(),
    enhancement: 0 as EnhancementLevel,
    onEnhancementChange: vi.fn(),
    ...rest,
  }
  return { props, ...render(<StartBuffCard {...props} />) }
}

function spriteSources(container: HTMLElement): string[] {
  return [
    ...Array.from(container.querySelectorAll('img')).map((img) => img.getAttribute('src') ?? ''),
    ...Array.from(container.querySelectorAll('[data-sprite]')).map(
      (node) => node.getAttribute('data-sprite') ?? '',
    ),
  ]
}

function spriteFiles(container: HTMLElement): string[] {
  const byPath = new Map(
    [...layoutFiles(), 'icon-107.webp'].map((file) => [getStartBuffSpritePath(7, file), file]),
  )
  return spriteSources(container).map((src) => byPath.get(src) ?? src)
}

afterEach(() => {
  textTables.pending = null
})

describe('StartBuffCard', () => {
  it('renders the card button alongside its two level buttons', () => {
    renderCard()

    expect(screen.getByRole('button', { name: '완전한 가능성' })).toBeInTheDocument()
    expect(screen.getAllByRole('button')).toHaveLength(3)
  })

  it('selects the buff when the card is clicked and releases it when selected', () => {
    const onSelect = vi.fn()
    const { rerender, props } = renderCard({ onSelect })

    fireEvent.click(screen.getByRole('button', { name: '완전한 가능성' }))
    rerender(<StartBuffCard {...props} isSelected />)
    fireEvent.click(screen.getByRole('button', { name: '완전한 가능성' }))

    expect(onSelect.mock.calls).toEqual([
      [109, true],
      [109, false],
    ])
  })

  it('toggles selection when the description itself is clicked', () => {
    const onSelect = vi.fn()
    renderCard({ onSelect })

    fireEvent.click(screen.getByTestId('start-buff-description'))

    expect(onSelect).toHaveBeenCalledWith(109, true)
  })

  it('sets and clears the enhancement from its level buttons', () => {
    const onEnhancementChange = vi.fn()
    const { rerender, props } = renderCard({ onEnhancementChange })

    fireEvent.click(screen.getByRole('button', { name: 'tierLabel.enhancement 2' }))
    rerender(<StartBuffCard {...props} enhancement={2} />)
    fireEvent.click(screen.getByRole('button', { name: 'tierLabel.enhancement 2' }))

    expect(onEnhancementChange.mock.calls).toEqual([
      [109, 2],
      [109, 0],
    ])
  })

  it('draws every sprite from the layout files of its season', () => {
    const { container } = renderCard({ isSelected: true, enhancement: 2 })
    const files = layoutFiles()

    expect(spriteFiles(container).every((file) => files.has(file))).toBe(true)
    expect(spriteFiles(container)).toContain('icon-109.webp')
  })

  it('shows the selected frame only while selected', () => {
    const { container: selected } = renderCard({ isSelected: true })
    const { container: unselected } = renderCard()

    expect(spriteFiles(selected)).toContain('MainUI_MirrorDungeon7_2_2.webp')
    expect(spriteFiles(unselected)).not.toContain('MainUI_MirrorDungeon7_2_2.webp')
  })

  it('shows the hover overlay while the pointer is over the card', () => {
    const { container } = renderCard()
    const root = container.firstElementChild as HTMLElement

    expect(spriteFiles(container)).not.toContain('MainUI_MirrorDungeon7_2_2__ffffff96.webp')
    fireEvent.mouseEnter(root)
    expect(spriteFiles(container)).toContain('MainUI_MirrorDungeon7_2_2__ffffff96.webp')
  })

  it.each([
    [0, 3, null],
    [1, 2, 'MainUI_MirrorDungeon3_3_22.webp'],
    [2, 1, 'MainUI_MirrorDungeon3_3_23.webp'],
  ] as const)(
    'at enhancement %i draws %i level icons and the focused icon %s',
    (enhancement, icons, focusedIcon) => {
      const { container } = renderCard({ enhancement })
      const files = spriteFiles(container)

      expect(
        files.filter((file) => file === 'MainUI_MirrorDungeon6_2_9__ebcaa2ff.webp'),
      ).toHaveLength(icons)
      expect(files.filter((file) => file.startsWith('MainUI_MirrorDungeon3_3_'))).toEqual(
        focusedIcon === null ? [] : [focusedIcon],
      )
    },
  )

  it.each([
    [0, '#ebcaa2'],
    [1, '#f8c200'],
    [2, '#f8c200'],
  ] as const)('colours the 109 cost at enhancement %i %s', (enhancement, color) => {
    renderCard({ enhancement })

    expect(screen.getByTestId('start-buff-cost-line')).toHaveStyle({ color })
  })

  it('draws the description lines the layout lays out', () => {
    renderCard({ enhancement: 2 })

    const lines = screen
      .getAllByTestId('start-buff-description-line')
      .map((line) => line.textContent)

    expect(lines.slice(0, 3)).toEqual([
      '• 테마팩 목록 +1',
      '• 시작 기프트 수 +1',
      '• 입장 시, 선택한 키워드의',
    ])
  })

  it('shows the bottom arrow only when the description overflows', () => {
    const { container: long } = renderCard({ enhancement: 2 })
    const { container: short } = renderCard({ baseId: 107 })

    expect(spriteFiles(long)).toContain('MainUI_MirrorDungeon5_5_5__44a782ff.webp')
    expect(spriteFiles(short)).not.toContain('MainUI_MirrorDungeon5_5_5__44a782ff.webp')
  })

  it('names the enhanced buff with its suffix', () => {
    renderCard({ enhancement: 2 })

    const name = within(screen.getByTestId('start-buff-name'))
      .getAllByTestId('start-buff-name-line')
      .map((line) => line.textContent)
      .join(' ')

    expect(name).toBe('완전한 가능성++')
  })

  it('keeps keyboard focus on the level button it switches from + to ++', async () => {
    const user = userEvent.setup()
    let enhancement: EnhancementLevel = 1
    const { rerender, props } = renderCard({
      enhancement,
      onEnhancementChange: (_baseId: number, level: EnhancementLevel) => {
        enhancement = level
      },
    })

    screen.getByRole('button', { name: 'tierLabel.enhancement 1' }).focus()
    await user.tab()
    await user.keyboard('{Enter}')
    rerender(<StartBuffCard {...props} enhancement={enhancement} />)

    expect(enhancement).toBe(2)
    expect(document.activeElement).toBe(
      screen.getByRole('button', { name: 'tierLabel.enhancement 2' }),
    )
  })

  it('keeps the sprites drawn while the text tables load', () => {
    textTables.pending = new Promise<never>(() => {})
    const { container } = renderCard()

    expect(spriteFiles(container)).toEqual(
      expect.arrayContaining(['MainUI_MirrorDungeon7_2_0.webp', 'icon-109.webp']),
    )
    expect(screen.queryByTestId('start-buff-name')).toBeNull()
    expect(screen.queryByTestId('start-buff-description')).toBeNull()
  })

  it('draws the bullet left-aligned in its advance box in the fallback face', () => {
    renderCard()

    const bullet = screen.getAllByTestId('start-buff-description-bullet')[0]

    expect(bullet?.style.textAlign).toBe('left')
    expect(bullet?.style.fontFamily).toBe('var(--font-pretendard)')
  })
})

describe('StartBuffCard name text rendering', () => {
  it('marks the name root as game text', () => {
    renderCard()

    expect(screen.getByText('Test Buff')).toHaveClass('game-text')
  })
})
