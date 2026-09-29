import type { CSSProperties } from 'react'

import { COIN_CLIENT_WIDTH, CLIENT_TEXT_SCALE } from '@/lib/constants'
import { clientUnitsToRem } from '@/lib/utils'
import { getCoinSpritePath } from '@/shared/assets'
import type { CoinColor, SkillCoin } from '@/shared/gameData'
import { isCoinColor } from '../lib/coinColor'

const COIN_ALT: Record<CoinColor, string> = {
  GOLD: 'Coin',
  GREY: 'Unbreakable coin',
  GREEN: 'Green coin',
  PURPLE: 'Purple coin',
}

const COIN_WIDTH = clientUnitsToRem(
  COIN_CLIENT_WIDTH,
  CLIENT_TEXT_SCALE.FONT_UNITS,
  CLIENT_TEXT_SCALE.FONT_REM,
)

const COIN_ROW_STYLE = { '--coin-width': COIN_WIDTH } as CSSProperties

interface CoinDisplayProps {
  coins: readonly SkillCoin[]
}

export function CoinDisplay({ coins }: CoinDisplayProps) {
  const colors = coins.map((coin) => coin.color).filter(isCoinColor)
  if (colors.length === 0) {
    return null
  }

  return (
    <div className="flex items-center mb-0.5" style={COIN_ROW_STYLE}>
      {colors.map((color, index) => (
        <img
          key={index}
          src={getCoinSpritePath(color)}
          alt={COIN_ALT[color]}
          className="w-(--coin-width) h-auto"
        />
      ))}
    </div>
  )
}
