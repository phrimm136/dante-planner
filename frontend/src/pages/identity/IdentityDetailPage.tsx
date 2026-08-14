import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useIdentityDetailSpec } from '@/pages/identity'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { DetailRightPanel } from '@/components/layout/DetailRightPanel'
import { MobileDetailTabs } from '@/components/layout/MobileDetailTabs'
import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEGOTierIconPath } from '@/shared/assets'
import { MAX_LEVEL, MAX_ENTITY_TIER, MIN_ENTITY_TIER } from '@/shared/gameData'
import { IdentityInfoPane } from './components/IdentityInfoPane'
import { IdentitySkillsPane } from './components/IdentitySkillsPane'
import { IdentityPassivesPane } from './components/IdentityPassivesPane'
import { IdentitySanityPane } from './components/IdentitySanityPane'
import type { Uptie } from '@/pages/identity'

// =============================================================================
// Main Content Component
// =============================================================================

/**
 * Inner content component that uses spec data only in shell.
 * I18n data is fetched in child components wrapped in Suspense.
 */
function IdentityDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  // Controllable uptie and level state
  const [uptie, setUptie] = useState<number>(MAX_ENTITY_TIER.identity)
  const [level, setLevel] = useState<number>(MAX_LEVEL)

  // Progressive rendering: render sections one-by-one (start immediately)
  // Sections: 1=Skills, 2=Passives, 3=Sanity
  const totalSections = 3
  const visibleSections = useProgressiveCount({ total: totalSections, step: 1, initial: 0 })

  // Route validation - id must be defined
  if (!id) {
    throw new Error('Identity ID is required')
  }

  // Spec data only - no language key, won't re-suspend on language change
  const identityData = useIdentityDetailSpec(id)

  // Cast to Uptie type for component props
  const uptieLevel = uptie as Uptie

  // Selector component (shared between desktop and mobile)
  const selector = (
    <DetailEntitySelector
      tierLabel={t('tierLabel.uptie')}
      minTier={MIN_ENTITY_TIER.identity}
      maxTier={MAX_ENTITY_TIER.identity}
      tier={uptie}
      onTierChange={setUptie}
      tierIconPath={getEGOTierIconPath}
      level={level}
      onLevelChange={setLevel}
      sticky
    />
  )

  // Left column: Header, Status, Resistance, Stagger, Traits (NO Sanity)
  const leftColumn = (
    <IdentityInfoPane id={id} identity={identityData} uptie={uptieLevel} level={level} />
  )

  // Skills content (shared between desktop and mobile)
  const skillsContent = (
    <IdentitySkillsPane id={id} skills={identityData.skills} uptieLevel={uptieLevel} />
  )

  // Passives content - PassiveCardI18n uses internal granular Suspense
  const passivesContent = (
    <IdentityPassivesPane id={id} passives={identityData.passives} uptieLevel={uptieLevel} />
  )

  // Sanity content (moved to right column, also used in mobile tabs)
  // Components use internal granular Suspense - no outer wrapper needed
  const sanityContent = (
    <IdentitySanityPane
      panicType={identityData.panicType}
      mentalConditionInfo={identityData.mentalConditionInfo}
    />
  )

  // Desktop right column: Selector (sticky) + Skills + Passives + Sanity
  // Progressive rendering: show sections one-by-one
  const rightColumn = (
    <DetailRightPanel selector={selector}>
      {visibleSections >= 1 && skillsContent}
      {visibleSections >= 2 && passivesContent}
      {visibleSections >= 3 && sanityContent}
    </DetailRightPanel>
  )

  // Mobile tabs: Skills, Passives, Sanity (Info is shown above via leftColumn)
  // Progressive rendering: show tabs when all sections loaded
  const mobileTabsContent =
    visibleSections >= totalSections ? (
      <>
        {/* Selector above tabs on mobile */}
        <div className="mb-4">{selector}</div>
        <MobileDetailTabs
          skillsContent={skillsContent}
          passivesContent={passivesContent}
          thirdTabContent={sanityContent}
        />
      </>
    ) : (
      <>
        {/* Show selector while loading, then skills when available */}
        <div className="mb-4">{selector}</div>
        {visibleSections >= 1 && skillsContent}
      </>
    )

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={mobileTabsContent}
    />
  )
}

// =============================================================================
// Page Export
// =============================================================================

/**
 * IdentityDetailPage - Identity detail page with two-column layout
 *
 * Desktop: 4:6 ratio with sticky selector in right column
 * Mobile: Info at top, then tabbed content (Skills/Passives/Sanity)
 *
 * Uses nested Suspense boundaries for granular loading:
 * - Shell (layout + stats) uses spec data - stable on language change
 * - Text sections use i18n data - suspend independently on language change
 */
export default function IdentityDetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="identity" />}>
      <IdentityDetailContent />
    </Suspense>
  )
}
