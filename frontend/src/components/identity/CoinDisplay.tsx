import { getCoinIconPath } from '@/lib/identityUtils'

interface CoinDisplayProps {
  coinEA: string
}

/**
 * CoinDisplay - Renders coin icons based on coin EA string
 *
 * Coin EA format: 'C' for normal coin, 'U' for unbreakable coin
 * Example: "CCU" renders two normal coins and one unbreakable coin
 */
export function CoinDisplay({ coinEA }: CoinDisplayProps) {
  if (!coinEA || coinEA.length === 0) {
    return null
  }

  return (
    <div className="flex items-center gap-1">
      {Array.from(coinEA).map((coin, index) => {
        const isUnbreakable = coin === 'U'
        const iconPath = getCoinIconPath(isUnbreakable ? 'U' : 'C')

        return (
          <img
            key={index}
            src={iconPath}
            alt={isUnbreakable ? 'Unbreakable coin' : 'Coin'}
            className="w-5 h-5"
          />
        )
      })}
    </div>
  )
}
