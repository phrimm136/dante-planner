import { useParams } from '@tanstack/react-router'
import GiftImage from '@/components/egoGift/GiftImage'
import GiftName from '@/components/egoGift/GiftName'
import CostDisplay from '@/components/egoGift/CostDisplay'
import EnhancementLevels from '@/components/egoGift/EnhancementLevels'
import AcquisitionMethod from '@/components/egoGift/AcquisitionMethod'
import { useEGOGiftDetailData } from '@/hooks/useEGOGiftDetailData'

export default function EGOGiftDetailPage() {
  const { id } = useParams({ strict: false }) as { id: string }

  // Hooks must be called unconditionally - route should validate id exists
  const { spec: giftSpec, i18n: giftI18n } = useEGOGiftDetailData(id)

  // Extract tier from tag array (e.g., "TIER_2" -> "2", "TIER_EX" -> "EX")
  // If TIER_EX exists, use it; otherwise use any TIER_ tag
  const exTier = giftSpec.tag.find(t => t === 'TIER_EX')
  const tier = exTier
    ? 'EX'
    : giftSpec.tag.find(t => t.startsWith('TIER_'))!.replace('TIER_', '')

  return (
    <div className="container mx-auto p-6">
      {/* Two-column grid layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Column */}
        <div className="space-y-4">
          {/* Horizontal layout: Image + Name-Cost vertical pair */}
          <div className="flex gap-4 items-start border rounded p-4">
            <GiftImage id={id} />
            <div className="flex-1 space-y-4">
              <GiftName name={giftI18n.name} />
              <CostDisplay cost={giftSpec.price} />
            </div>
          </div>
          <AcquisitionMethod obtain={giftI18n.obtain} />
        </div>

        {/* Right Column */}
        <div>
          <EnhancementLevels descs={giftI18n.descs} tier={tier} />
        </div>
      </div>
    </div>
  )
}
