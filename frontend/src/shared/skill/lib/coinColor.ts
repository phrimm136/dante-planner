import { COIN_COLORS } from '@/shared/gameData'
import type { CoinColor } from '@/shared/gameData'

export function isCoinColor(color: string): color is CoinColor {
  return (COIN_COLORS as readonly string[]).includes(color)
}
