import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'

import enemySpec from '@static/data/enemySpecList.json'
import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import type { BattleCase } from '../../lib/battleVocabulary'
import { BATTLE_GEOMETRY } from '../../lib/cardLayout'
import { EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { BattleCard } from '../BattleCard'

const SPEC = EnemySpecListSchema.parse(enemySpec)

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

function renderCard(id: string, { hover = true, mode = 'story' as BattleCase } = {}) {
  const entry = SPEC[id as keyof typeof SPEC]
  if (entry === undefined) throw new Error(`no enemy ${id}`)
  renderWithProviders(
    <BattleCard
      family="enemy"
      id={id}
      spriteId={entry.spriteId}
      sprite={entry.sprite}
      mode={mode}
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

  it('places the sprite at 45 px per world unit with its anchor 35 % below the top', () => {
    renderCard('1133')

    const style = spriteImage().style
    expect(style.position).toBe('absolute')
    expect(style.left).toBe('50%')
    expect(style.top).toBe('35%')
    expect(style.marginLeft).toBe('-558.13px')
    expect(style.marginTop).toBe('-135.45px')
    expect(style.transformOrigin).toBe('558.13px 135.45px')
    expect(style.transform).toBe(`scale(${String(45 / 240)})`)
    expect(style.maxWidth).toBe('none')
    expect(screen.getByTestId('battle-card-sprite')).toHaveClass('overflow-hidden')
  })

  it('contains a sprite without a placement in its box', () => {
    renderCard('1288')

    expect(spriteImage()).toHaveClass('object-contain')
    expect(spriteImage().style.transform).toBe('')
  })

  it('draws the name below the sprite, per mode where the mode names it differently', async () => {
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

  it('carries the abEvent hover highlight only when enabled', () => {
    renderCard('1133')
    expect(screen.getByTestId('battle-card-sprite')).toHaveClass('selectable')
  })

  it('omits the hover highlight by default', () => {
    renderCard('1133', { hover: false })
    expect(screen.getByTestId('battle-card-sprite')).not.toHaveClass('selectable')
  })

  it('falls back to the id when the sprite fails to load', () => {
    renderCard('1133')

    fireEvent.error(spriteImage())

    expect(screen.getByTestId('battle-card-sprite')).toHaveTextContent('1133')
    expect(screen.getByTestId('battle-card-sprite').querySelector('img')).toBeNull()
  })
})
