import { getCoinSpritePath } from '@/shared/assets'
import type { CoinColor, SkillCoin } from '@/shared/gameData'
import { isCoinColor } from '../lib/coinColor'

const COIN_ALT: Record<CoinColor, string> = {
  GOLD: 'Coin',
  GREY: 'Unbreakable coin',
  GREEN: 'Green coin',
  PURPLE: 'Purple coin',
}

interface CoinDisplayProps {
  coins: readonly SkillCoin[]
}

export function CoinDisplay({ coins }: CoinDisplayProps) {
  const colors = coins.map((coin) => coin.color).filter(isCoinColor)
  if (colors.length === 0) {
    return null
  }

  return (
    <div className="flex items-center mb-0.5">
      {colors.map((color, index) => (
        <img key={index} src={getCoinSpritePath(color)} alt={COIN_ALT[color]} className="w-6 h-6" />
      ))}
    </div>
  )
}
