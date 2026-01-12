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
import { GiftNameI18n } from '@/components/egoGift/GiftNameI18n'
import { EGOGiftMetadata } from '@/components/egoGift/EGOGiftMetadata'
import { EnhancementsPanelI18n } from '@/components/egoGift/EnhancementsPanelI18n'
import { RecipeSection } from '@/components/egoGift/RecipeSection'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { useEGOGiftDetailSpec } from '@/hooks/useEGOGiftDetailData'
import { ENHANCEMENT_LEVELS } from '@/lib/constants'
import {
  calculateEnhancementCost,
  extractEGOGiftTier,
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

  // Fetch spec data only (stable - no language dependency)
  const giftData = useEGOGiftDetailSpec(id)

  // Extract tier from tag array using utility
  const tier = extractEGOGiftTier(giftData.tag)

  // Max enhancement from spec data (language-independent)
  const maxEnhancement = giftData.maxEnhancement

  // Calculate costs for all enhancement levels
  const enhancementCosts = ENHANCEMENT_LEVELS.map((level) =>
    calculateEnhancementCost(tier, level)
  )

  // Construct gift object for EGOGiftCard (spec data only for stable card display)
  // Type assertion needed: Zod validates tag has TIER_* at runtime,
  // but schema outputs string[] not the branded type
  const gift = {
    id,
    tag: giftData.tag,
    keyword: giftData.keyword,
    attributeType: giftData.attributeType,
    themePack: giftData.themePack,
    hardOnly: giftData.hardOnly,
    extremeOnly: giftData.extremeOnly,
  } as EGOGiftListItem

  // Left column: Header (card + name), Metadata
  const leftColumn = (
    <div className="space-y-4">
      {/* Header row: Card + Name (vertically centered) */}
      <div className="flex gap-4 items-center">
        <EGOGiftCard
          gift={gift}
          enhancement={0}
        />
        {/* Name with internal Suspense - does not suspend parent */}
        <GiftNameI18n id={id} attributeType={giftData.attributeType as import('@/lib/constants').EGOGiftAttributeType} />
      </div>

      {/* Metadata panel - internal Suspense for theme pack names only */}
      <EGOGiftMetadata
        price={giftData.price}
        themePack={giftData.themePack}
        hardOnly={giftData.hardOnly}
        extremeOnly={giftData.extremeOnly}
        maxEnhancement={maxEnhancement}
      />
    </div>
  )

  // Right column: All enhancement descriptions + recipe section
  const rightColumn = (
    <div className="space-y-4">
      <EnhancementsPanelI18n
        giftId={id}
        maxEnhancement={maxEnhancement}
        costs={enhancementCosts}
      />
      {giftData.recipe && <RecipeSection recipe={giftData.recipe} />}
    </div>
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
    <Suspense fallback={<DetailPageSkeleton preset="egoGift" />}>
      <EGOGiftDetailContent />
    </Suspense>
  )
}
