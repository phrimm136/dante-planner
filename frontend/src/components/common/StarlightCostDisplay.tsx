import { getStartBuffStarLightPath } from '@/lib/assetPaths'

interface StarlightCostDisplayProps {
  cost: number
  isEnhanced?: boolean
  size?: 'sm' | 'md' | 'lg'
}

/**
 * Starlight cost display component
 * Shows starlight icon + cost number (right-aligned)
 * Number grows to the right to prevent icon shifting
 */
export function StarlightCostDisplay({
  cost,
  isEnhanced = false,
  size = 'md',
}: StarlightCostDisplayProps) {
  const iconSize = {
    sm: 'w-4 h-4',
    md: 'w-6 h-6',
    lg: 'w-8 h-8',
  }[size]

  const textSize = {
    sm: 'text-xs',
    md: 'text-sm',
    lg: 'text-base',
  }[size]

  return (
    <div className="flex items-center gap-1 justify-end">
      <img
        src={getStartBuffStarLightPath()}
        alt=""
        className={`${iconSize} object-contain shrink-0`}
      />
      <span
        className={`font-bold ${textSize} text-left min-w-[3ch]`}
        style={{ color: isEnhanced ? '#f8c200' : 'white' }}
      >
        {cost}
      </span>
    </div>
  )
}
