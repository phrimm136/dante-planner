import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'

import enemySpec from '@static/data/enemySpecList.json'
import identityNamesKR from '@static/i18n/KR/identityNameList.json'
import i18n from '@/lib/i18n'
import { getBattleListImagePath } from '@/shared/assets'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { envyIdentityTitle } from '../../lib/battleListing'
import type { BattleCase } from '../../lib/battleVocabulary'
import { BATTLE_GEOMETRY } from '../../lib/cardLayout'
import { EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { BattleCard } from '../BattleCard'

const SPEC = EnemySpecListSchema.parse(enemySpec)

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

function renderCard(
  id: string,
  {
    hover = true,
    mode = 'story' as BattleCase,
    identityTitle = undefined as string | undefined,
  } = {},
) {
  if (SPEC[id as keyof typeof SPEC] === undefined) throw new Error(`no enemy ${id}`)
  renderWithProviders(
    <BattleCard
      family="enemy"
      id={id}
      mode={mode}
      identityTitle={identityTitle}
      enableHoverHighlight={hover}
    />,
  )
}

function spriteImage(): HTMLImageElement {
  const img = screen.getByTestId('battle-card-sprite').querySelector('img')
  if (img === null) throw new Error('no sprite image')
  return img
}

describe('BattleCard', () => {
  it('declares a box whose longer side is 256 px', () => {
    const { widthPx, heightPx } = BATTLE_GEOMETRY.size
    expect(Math.max(widthPx, heightPx)).toBe(256)
    expect(BATTLE_GEOMETRY.mobileScale).toBeGreaterThan(0)
  })

  it('covers its box with the list image of its card', () => {
    renderCard('1126')

    expect(spriteImage().getAttribute('src')).toBe(getBattleListImagePath('enemy', '1126'))
    expect(spriteImage()).toHaveClass('h-full', 'w-full', 'object-cover')
    expect(spriteImage().getAttribute('style')).toBeNull()
    expect(screen.getByTestId('battle-card-sprite')).toHaveClass('overflow-hidden')
  })

  it('covers the box of the Fools card 8999 with its shipped list image', () => {
    renderCard('8999')

    expect(spriteImage().getAttribute('src')).toBe(getBattleListImagePath('enemy', '8999'))
    expect(getBattleListImagePath('enemy', '8999')).not.toBe('/images/battleList/enemy/8999.webp')
  })

  it('draws the name below the image, per mode where the mode names it differently', async () => {
    renderCard('1152', { mode: 'story' })

    const sprite = screen.getByTestId('battle-card-sprite')
    const name = screen.getByTestId('battle-card-name')
    expect(sprite.compareDocumentPosition(name) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(await screen.findByText('Jia Mu')).toBeInTheDocument()
  })

  it('draws the group name in a mode without its own name', async () => {
    renderCard('1152', { mode: 'refractionRailway' })

    expect(
      await screen.findByText('Muddled Heishou of the Corpse Palanquin [混覺轀輬黑獸]'),
    ).toBeInTheDocument()
  })

  it.each([
    ['story', '질투 죄종? - W사 3등급 정리 요원'],
    ['refractionRailway', '굴절된 질투 죄종? - W사 3등급 정리 요원'],
    ['mirrorDungeon', '굴절된 질투 죄종? - W사 3등급 정리 요원'],
  ] as const)('names the envy card 1099 by unit and copied identity in %s', async (mode, text) => {
    await i18n.changeLanguage('KR')
    const identityTitle = envyIdentityTitle(
      SPEC['1099' as keyof typeof SPEC]?.personalityId,
      identityNamesKR,
    )
    renderCard('1099', { mode, identityTitle })

    expect(await screen.findByText(text)).toBeInTheDocument()
  })

  it('names the envy card 1110 by unit and its South Liu identity on one line', async () => {
    await i18n.changeLanguage('KR')
    const identityTitle = envyIdentityTitle(
      SPEC['1110' as keyof typeof SPEC]?.personalityId,
      identityNamesKR,
    )
    expect(SPEC['1110' as keyof typeof SPEC]?.personalityId).toBe(10407)
    renderCard('1110', { identityTitle })

    expect(await screen.findByText('질투 죄종 - 남부 리우 협회 4과')).toBeInTheDocument()
  })

  it('carries the abEvent hover highlight only when enabled', () => {
    renderCard('1126')
    expect(screen.getByTestId('battle-card-sprite')).toHaveClass('selectable')
  })

  it('omits the hover highlight by default', () => {
    renderCard('1126', { hover: false })
    expect(screen.getByTestId('battle-card-sprite')).not.toHaveClass('selectable')
  })

  it('shows the card id in place of a list image that fails to load', () => {
    renderCard('1126')

    fireEvent.error(spriteImage())

    expect(screen.getByTestId('battle-card-sprite')).toHaveTextContent('1126')
    expect(screen.getByTestId('battle-card-sprite').querySelector('img')).toBeNull()
  })
})
