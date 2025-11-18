import { getEGOGiftCoinIconPath } from '@/lib/assetPaths'

interface CostDisplayProps {
  cost: number
}

export default function CostDisplay({ cost }: CostDisplayProps) {
  return (
    <div className="flex items-center gap-2">
      <img
        src={getEGOGiftCoinIconPath()}
        alt="Cost"
        className="w-6 h-6"
      />
      <span className="text-lg font-semibold">{cost}</span>
    </div>
  )
}
