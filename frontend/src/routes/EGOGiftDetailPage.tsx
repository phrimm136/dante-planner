/**
 * EGOGiftDetailPage - EGO Gift detail page with two-column layout
 *
 * Desktop: 4:6 ratio with sticky enhancement selector in right column
 * Mobile: Single column with all content stacked (no tabs)
 *
 * Pattern Source: IdentityDetailPage.tsx
 */

import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'

import GiftImage from '@/components/egoGift/GiftImage'
import GiftName from '@/components/egoGift/GiftName'
import { EGOGiftMetadata } from '@/components/egoGift/EGOGiftMetadata'
import EnhancementPanel from '@/components/egoGift/EnhancementPanel'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailEntitySelector } from '@/components/common/DetailEntitySelector'
import { DetailRightPanel } from '@/components/common/DetailRightPanel'
import { LoadingState } from '@/components/common/LoadingState'
import { useEGOGiftDetailData } from '@/hooks/useEGOGiftDetailData'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { MIN_ENTITY_TIER } from '@/lib/constants'
import {
  calculateEnhancementCost,
  extractEGOGiftTier,
  getDisabledEnhancementLevels,
} from '@/lib/egoGiftUtils'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGOGiftDetailContent() {
  const { id } = useParams({ strict: false })

  // Throw error if id is missing - ErrorBoundary will catch this
  if (!id) {
    throw new Error('EGO Gift ID is required')
  }

  // Enhancement level state (0, 1, 2)
  const [enhancementLevel, setEnhancementLevel] = useState<number>(
    MIN_ENTITY_TIER.egoGift
  )

  // Fetch detail data (includes all fields: price, tag, keyword, themePack, hardOnly, extremeOnly)
  const { spec: giftData, i18n: giftI18n } = useEGOGiftDetailData(id)

  // Fetch theme pack names for display
  const { themePackI18n } = useThemePackListData()

  // Extract tier from tag array using utility
  const tier = extractEGOGiftTier(giftData.tag)

  // Calculate disabled tiers using utility
  const disabledTiers = getDisabledEnhancementLevels(giftI18n.descs)

  // Selector component (shared between desktop and mobile)
  const selector = (
    <DetailEntitySelector
      entityType="egoGift"
      tier={enhancementLevel}
      onTierChange={setEnhancementLevel}
      disabledTiers={disabledTiers}
      sticky
    />
  )

  // Left column: Header (image + name), Metadata
  const leftColumn = (
    <div className="space-y-4">
      {/* Header row: Image + Name */}
      <div className="flex gap-4 items-start">
        <GiftImage id={id} />
        <div className="flex-1 space-y-2">
          <GiftName name={giftI18n.name} />
        </div>
      </div>

      {/* Metadata panel */}
      <EGOGiftMetadata
        keyword={giftData.keyword}
        price={giftData.price}
        themePack={giftData.themePack}
        themePackNames={themePackI18n}
        hardOnly={giftData.hardOnly}
        extremeOnly={giftData.extremeOnly}
      />
    </div>
  )

  // Enhancement description content (shows selected level)
  const enhancementContent = (
    <EnhancementPanel
      description={giftI18n.descs[enhancementLevel] ?? ''}
      level={enhancementLevel}
      cost={calculateEnhancementCost(tier, enhancementLevel)}
    />
  )

  // Desktop right column: Selector (sticky) + Enhancement description
  const rightColumn = (
    <DetailRightPanel selector={selector}>{enhancementContent}</DetailRightPanel>
  )

  // Mobile: Single scroll with selector + content (no tabs)
  const mobileContent = (
    <div className="space-y-4">
      {selector}
      {enhancementContent}
    </div>
  )

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
