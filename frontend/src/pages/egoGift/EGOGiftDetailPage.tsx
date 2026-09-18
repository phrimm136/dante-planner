import { useParams } from '@tanstack/react-router'
import { Suspense } from 'react'

import { EGOGiftCard } from '@/pages/egoGift'
import { CardSlot, EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import { GiftNameI18n } from '@/pages/egoGift'
import { EGOGiftMetadata } from '@/pages/egoGift'
import { EnhancementsPanelI18n } from '@/pages/egoGift'
import { RecipeSection } from '@/pages/egoGift'
import { EGOGiftDetailSkeleton } from '@/pages/egoGift'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { KeywordsDisplay } from '@/shared/gameText'
import { useEGOGiftDetailSpec } from '@/pages/egoGift'
import { ENHANCEMENT_LEVELS } from '@/shared/gameData'
import { calculateEnhancementCost, parseTier } from '@/pages/egoGift'
import type { EGOGiftEntity } from '@/pages/egoGift'

function EGOGiftDetailContent() {
  const { id } = useParams({ strict: false })

  if (!id) {
    throw new Error('EGO Gift ID is required')
  }

  const giftData = useEGOGiftDetailSpec(id)

  const tier = parseTier(giftData.tag)

  const maxEnhancement = giftData.maxEnhancement

  const enhancementCosts = ENHANCEMENT_LEVELS.map((level) =>
    tier === null ? null : calculateEnhancementCost(tier, level),
  )

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
  } as EGOGiftEntity

  const leftColumn = (
    <div className="space-y-4">
      <div className="flex gap-4 items-center">
        <CardSlot
          size={EGO_GIFT_GEOMETRY.size}
          mobileScale={CARD_MOBILE_SCALE_NONE}
          className="shrink-0"
        >
          <EGOGiftCard gift={gift} enhancement={0} />
        </CardSlot>
        <GiftNameI18n
          id={id}
          attributeType={giftData.attributeType as import('@/shared/gameData').EGOGiftAttributeType}
        />
      </div>

      <EGOGiftMetadata
        price={giftData.price}
        themePack={giftData.themePack}
        hardOnly={giftData.hardOnly}
        extremeOnly={giftData.extremeOnly}
        maxEnhancement={maxEnhancement}
      />

      <KeywordsDisplay keywords={giftData.battleKeywordList} />
    </div>
  )

  const rightColumn = (
    <div className="space-y-4">
      <EnhancementsPanelI18n giftId={id} maxEnhancement={maxEnhancement} costs={enhancementCosts} />
      {giftData.recipe && <RecipeSection recipe={giftData.recipe} />}
    </div>
  )

  const mobileContent = rightColumn

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={mobileContent}
    />
  )
}

export default function EGOGiftDetailPage() {
  return (
    <Suspense fallback={<EGOGiftDetailSkeleton />}>
      <EGOGiftDetailContent />
    </Suspense>
  )
}
