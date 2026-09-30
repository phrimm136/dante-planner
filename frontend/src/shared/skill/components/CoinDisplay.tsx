import type { CSSProperties } from 'react'

import { COIN_CLIENT_SPACING, COIN_CLIENT_WIDTH } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import { getCoinSpritePath } from '@/shared/assets'
import type { CoinColor, SkillCoin } from '@/shared/gameData'
import { isCoinColor } from '../lib/coinColor'

const COIN_ALT: Record<CoinColor, string> = {
  GOLD: 'Coin',
  GREY: 'Unbreakable coin',
  GREEN: 'Green coin',
  PURPLE: 'Purple coin',
}

const COIN_WIDTH = clientLength(COIN_CLIENT_WIDTH)

const COIN_GAP = clientLength(COIN_CLIENT_SPACING)

const COIN_ROW_STYLE = { '--coin-width': COIN_WIDTH, '--coin-gap': COIN_GAP } as CSSProperties

const OVERLAPPING_COIN_STYLE: CSSProperties = { marginInlineStart: 'var(--coin-gap)' }

interface CoinDisplayProps {
  coins: readonly SkillCoin[]
}

export function CoinDisplay({ coins }: CoinDisplayProps) {
  const colors = coins.map((coin) => coin.color).filter(isCoinColor)
  if (colors.length === 0) {
    return null
  }

  return (
    <div className="flex items-center" style={COIN_ROW_STYLE}>
      {colors.map((color, index) => (
        <img
          key={index}
          src={getCoinSpritePath(color)}
          alt={COIN_ALT[color]}
          className="w-(--coin-width) h-auto"
          style={index === 0 ? undefined : OVERLAPPING_COIN_STYLE}
        />
      ))}
    </div>
  )
}
