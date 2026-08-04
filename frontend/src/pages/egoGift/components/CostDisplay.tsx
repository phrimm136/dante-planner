import { getEGOGiftCostIconPath } from '@/shared/assets'
import { SECTION_STYLES } from '@/lib/constants'

interface CostDisplayProps {
  cost: number
}

export default function CostDisplay({ cost }: CostDisplayProps) {
  return (
    <div className={SECTION_STYLES.LAYOUT.row}>
      <img src={getEGOGiftCostIconPath()} alt="Cost" className="w-6 h-6" />
      <span className={SECTION_STYLES.TEXT.sectionTitle}>{cost}</span>
    </div>
  )
}
