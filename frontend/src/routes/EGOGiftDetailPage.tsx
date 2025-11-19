import { useParams } from '@tanstack/react-router'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'
import GiftImage from '@/components/egoGift/GiftImage'
import GiftName from '@/components/egoGift/GiftName'
import CostDisplay from '@/components/egoGift/CostDisplay'
import EnhancementLevels from '@/components/egoGift/EnhancementLevels'
import AcquisitionMethod from '@/components/egoGift/AcquisitionMethod'
import { useEntityDetailData } from '@/hooks/useEntityDetailData'
import type { EGOGiftData, EGOGiftI18n } from '@/types/EGOGiftTypes'

export default function EGOGiftDetailPage() {
  const { id } = useParams({ strict: false })

  // Validate id exists before making queries
  if (!id) {
    return (
      <ErrorState
        title="Invalid URL"
        message="No EGO Gift ID provided in the URL"
      />
    )
  }

  const { data, i18n, isPending, isError } =
    useEntityDetailData('egoGift', id)
  const giftSpec = data as EGOGiftData | undefined
  const giftI18n = i18n as EGOGiftI18n | undefined

  if (isPending) {
    return <LoadingState />
  }

  if (isError || !giftSpec || !giftI18n) {
    return (
      <ErrorState
        title="Gift Not Found"
        message={`Unable to load gift data for ID: ${id}`}
      />
    )
  }

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
              <CostDisplay cost={giftSpec.cost} />
            </div>
          </div>
          <AcquisitionMethod obtain={giftI18n.obtain} />
        </div>

        {/* Right Column */}
        <div>
          <EnhancementLevels descs={giftI18n.descs} tier={giftSpec.tier} />
        </div>
      </div>
    </div>
  )
}
