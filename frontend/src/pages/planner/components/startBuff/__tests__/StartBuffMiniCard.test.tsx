import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@/test-utils/renderWithProviders'

import md6KrNames from '@static/i18n/KR/MD6/startBuffs.json'
import { getStartBuffSpritePath } from '@/shared/assets'
import { StartBuffMiniCard } from '../StartBuffMiniCard'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'KR' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const NAMES = md6KrNames as Record<string, string>
const PLUS2_ICON = 'MainUI_MirrorDungeon3_3_23.webp'

function nameOf(baseId: number): string {
  const name = NAMES[`mirror_dungeon_5_buffs_title_${String(baseId)}`]
  if (name === undefined) throw new Error(`MD6 KR has no name for ${String(baseId)}`)
  return name
}

async function renderMini(buffId: number, baseId: number) {
  const view = render(
    <StartBuffMiniCard buffId={buffId} displayName={nameOf(baseId)} mdVersion={6} />,
  )
  await screen.findByTestId('start-buff-mini-name')
  return view
}

function nameLines(): string[] {
  return screen.getAllByTestId('start-buff-mini-name-line').map((line) => line.textContent)
}

const MD6_FILES = [
  'MainUI_MirrorDungeon6_4_2.webp',
  'MainUI_MirrorDungeon6_4_4.webp',
  'MainUI_MirrorDungeon3_3_22.webp',
  PLUS2_ICON,
  'mini-icon-101.webp',
  'mini-icon-105.webp',
  'mini-icon-108.webp',
]

function spriteFiles(container: HTMLElement): string[] {
  const byPath = new Map(MD6_FILES.map((file) => [getStartBuffSpritePath(6, file), file]))
  return Array.from(container.querySelectorAll('img')).map((img) => {
    const src = img.getAttribute('src') ?? ''
    return byPath.get(src) ?? src
  })
}

describe('StartBuffMiniCard', () => {
  it('shows the ++ level icon and wraps the MD6 105 ++ name', async () => {
    const { container } = await renderMini(305, 105)

    expect(spriteFiles(container)).toContain(PLUS2_ICON)
    expect(nameLines()).toEqual(['위성계', '상점++'])
  })

  it('shows no level icon at level 0', async () => {
    const { container } = await renderMini(105, 105)

    expect(
      spriteFiles(container).filter((file) => file.startsWith('MainUI_MirrorDungeon3_3_')),
    ).toEqual([])
  })

  it('wraps the MD6 108 ++ name', async () => {
    await renderMini(308, 108)

    expect(nameLines()).toEqual(['우연한', '혜성++'])
  })

  it('wraps the MD6 101 base name', async () => {
    await renderMini(101, 101)

    expect(nameLines()).toEqual(['쌓여가는', '별무리'])
  })

  it('draws every sprite from the MD6 layout files', async () => {
    const { container } = await renderMini(305, 105)

    expect(spriteFiles(container).every((file) => MD6_FILES.includes(file))).toBe(true)
    expect(spriteFiles(container)).toEqual(
      expect.arrayContaining(['MainUI_MirrorDungeon6_4_2.webp', 'mini-icon-105.webp']),
    )
  })

  it('shows the hover overlay while the pointer is over the card', async () => {
    const { container } = await renderMini(105, 105)
    const root = container.querySelector('[data-testid="start-buff-mini-name"]')?.parentElement

    expect(spriteFiles(container)).not.toContain('MainUI_MirrorDungeon6_4_4.webp')
    if (root) fireEvent.mouseEnter(root)
    expect(spriteFiles(container)).toContain('MainUI_MirrorDungeon6_4_4.webp')
  })
})
