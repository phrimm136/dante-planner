/**
 * EGOGiftDetailPage - EGO Gift detail page with two-column layout
 *
 * Desktop: 4:6 ratio with all enhancement descriptions in right column
 * Mobile: Single column with all content stacked
 *
 * Pattern Source: IdentityDetailPage.tsx
 */

import { useParams } from '@tanstack/react-router'
import { Suspense } from 'react'

import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import GiftName from '@/components/egoGift/GiftName'
import { EGOGiftMetadata } from '@/components/egoGift/EGOGiftMetadata'
import { AllEnhancementsPanel } from '@/components/egoGift/AllEnhancementsPanel'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { LoadingState } from '@/components/common/LoadingState'
import { useEGOGiftDetailData } from '@/hooks/useEGOGiftDetailData'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { ENHANCEMENT_LEVELS } from '@/lib/constants'
import {
  calculateEnhancementCost,
  extractEGOGiftTier,
  getMaxEnhancementLevel,
} from '@/lib/egoGiftUtils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGOGiftDetailContent() {
  const { id } = useParams({ strict: false })

  // Throw error if id is missing - ErrorBoundary will catch this
  if (!id) {
    throw new Error('EGO Gift ID is required')
  }

  // Fetch detail data (includes all fields: price, tag, keyword, themePack, hardOnly, extremeOnly)
  const { spec: giftData, i18n: giftI18n } = useEGOGiftDetailData(id)

  // Fetch theme pack names for display
  const { themePackI18n } = useThemePackListData()

  // Extract tier from tag array using utility
  const tier = extractEGOGiftTier(giftData.tag)

  // Calculate max enhancement level from available descriptions
  const maxEnhancement = getMaxEnhancementLevel(giftI18n.descs)

  // Calculate costs for all enhancement levels
  const enhancementCosts = ENHANCEMENT_LEVELS.map((level) =>
    calculateEnhancementCost(tier, level)
  )

  // Construct gift object for EGOGiftCard (combines spec + i18n data)
  const gift: EGOGiftListItem = {
    id,
    name: giftI18n.name,
    tag: giftData.tag,
    keyword: giftData.keyword,
    attributeType: giftData.attributeType,
    themePack: giftData.themePack,
    hardOnly: giftData.hardOnly,
    extremeOnly: giftData.extremeOnly,
  }

  // Left column: Header (card + name), Metadata
  const leftColumn = (
    <div className="space-y-4">
      {/* Header row: Card + Name (vertically centered) */}
      <div className="flex gap-4 items-center">
        <EGOGiftCard
          gift={gift}
          enhancement={maxEnhancement}
        />
        <GiftName attributeType={giftData.attributeType} name={giftI18n.name} />
      </div>

      {/* Metadata panel */}
      <EGOGiftMetadata
        price={giftData.price}
        themePack={giftData.themePack}
        themePackNames={themePackI18n}
        hardOnly={giftData.hardOnly}
        extremeOnly={giftData.extremeOnly}
        maxEnhancement={maxEnhancement}
      />
    </div>
  )

  // Right column: All enhancement descriptions stacked
  const rightColumn = (
    <AllEnhancementsPanel
      descriptions={giftI18n.descs}
      costs={enhancementCosts}
    />
  )

  // Mobile: Same content as desktop
  const mobileContent = rightColumn

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={mobileContent}
    />
  )
}

/**
 * EGOGiftDetailPage - Main export with Suspense boundary
 */
export default function EGOGiftDetailPage() {
  return (
    <Suspense fallback={<LoadingState message="Loading EGO Gift..." />}>
      <EGOGiftDetailContent />
    </Suspense>
  )
}
