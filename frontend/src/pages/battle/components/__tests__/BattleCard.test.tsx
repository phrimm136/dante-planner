import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ENEMY_LIST } from '../../hooks/useEnemyListData'
import { BATTLE_GEOMETRY } from '../../lib/cardLayout'
import { BattleCard } from '../BattleCard'

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

function renderCard(enableHoverHighlight: boolean) {
  renderWithProviders(
    <BattleCard
      family="enemy"
      id="1144"
      spriteId="1133_BlackHand_Mao_JaroAppearance"
      listConfig={ENEMY_LIST}
      enableHoverHighlight={enableHoverHighlight}
    />,
  )
}

describe('BattleCard', () => {
  it('declares a box whose longer side is 256 px', () => {
    const { widthPx, heightPx } = BATTLE_GEOMETRY.size
    expect(Math.max(widthPx, heightPx)).toBe(256)
    expect(BATTLE_GEOMETRY.mobileScale).toBeGreaterThan(0)
  })

  it('draws the sprite contained in its box with the name below it', async () => {
    renderCard(true)

    const sprite = screen.getByTestId('battle-card-sprite')
    const name = screen.getByTestId('battle-card-name')
    expect(sprite.querySelector('img')).toHaveClass('object-contain')
    expect(sprite.compareDocumentPosition(name) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(await screen.findByText('Zilu')).toBeInTheDocument()
    expect(name).toHaveTextContent('Zilu')
  })

  it('carries the abEvent hover highlight only when enabled', () => {
    renderCard(true)
    expect(screen.getByTestId('battle-card-sprite')).toHaveClass('selectable')
  })

  it('omits the hover highlight by default', () => {
    renderCard(false)
    expect(screen.getByTestId('battle-card-sprite')).not.toHaveClass('selectable')
  })

  it('falls back to the id when the sprite fails to load', () => {
    renderCard(true)
    const img = screen.getByTestId('battle-card-sprite').querySelector('img')
    if (img === null) throw new Error('no sprite image')

    fireEvent.error(img)

    expect(screen.getByTestId('battle-card-sprite')).toHaveTextContent('1144')
    expect(screen.getByTestId('battle-card-sprite').querySelector('img')).toBeNull()
  })
})
