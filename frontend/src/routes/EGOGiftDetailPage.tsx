import GiftImage from '@/components/gift/GiftImage'
import GiftName from '@/components/gift/GiftName'
import CostDisplay from '@/components/gift/CostDisplay'
import EnhancementLevels from '@/components/gift/EnhancementLevels'
import AcquisitionMethod from '@/components/gift/AcquisitionMethod'

export default function EGOGiftDetailPage() {
  // TODO: Extract ID from route params and load data during data integration phase

  return (
    <div className="container mx-auto p-6">
      {/* Three-column grid layout matching mockup */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column */}
        <div className="space-y-4">
          <GiftImage />
          <GiftName />
          <CostDisplay />
        </div>

        {/* Center Column */}
        <div>
          <EnhancementLevels />
        </div>

        {/* Right Column */}
        <div>
          <AcquisitionMethod />
        </div>
      </div>
    </div>
  )
}
