import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { DetailRightPanel } from '@/components/layout/DetailRightPanel'
import { MobileDetailTabs } from '@/components/layout/MobileDetailTabs'
import { useEGODetailSpec } from '@/pages/ego'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEGOTierIconPath } from '@/shared/assets'
import { MIN_ENTITY_TIER } from '@/shared/gameData'
import { EGOInfoPane } from './components/EGOInfoPane'
import { EGOSkillsPane } from './components/EGOSkillsPane'
import { EGOPassivesPane } from './components/EGOPassivesPane'
import type { Threadspin } from '@/pages/ego'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGODetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  // Progressive rendering: render sections one-by-one (start immediately)
  // Sections: 1=Skills, 2=Passives
  const totalSections = 2
  const visibleSections = useProgressiveCount({ total: totalSections, step: 1, initial: 0 })

  // Route validation - id must be defined
  if (!id) {
    throw new Error('EGO ID is required')
  }

  // Spec data only - no language key, won't re-suspend on language change
  const spec = useEGODetailSpec(id)

  // Controllable threadspin state — defaults to this EGO's max.
  const [threadspin, setThreadspin] = useState<number>(spec.maxThreadspin)

  // Cast to Threadspin type for component props
  const threadspinLevel = threadspin as Threadspin

  // Selector component (shared between desktop and mobile)
  const selector = (
    <DetailEntitySelector
      tierLabel={t('tierLabel.threadspin')}
      minTier={MIN_ENTITY_TIER.ego}
      maxTier={spec.maxThreadspin}
      tier={threadspin}
      onTierChange={setThreadspin}
      tierIconPath={getEGOTierIconPath}
      sticky
    />
  )

  // Left column: Header (with i18n), Sin Cost, Sin Resistance
  const leftColumn = <EGOInfoPane id={id} ego={spec} />

  // Skills content (shared between desktop and mobile)
  const skillsContent = (
    <EGOSkillsPane id={id} skills={spec.skills} threadspinLevel={threadspinLevel} />
  )

  // Passives content - PassiveCardWithSuspense uses internal granular Suspense
  const passivesContent = (
    <EGOPassivesPane id={id} passives={spec.passives} threadspinLevel={threadspinLevel} />
  )

  // Desktop right column: Selector (sticky) + Skills + Passives
  // Progressive rendering: show sections one-by-one
  const rightColumn = (
    <DetailRightPanel selector={selector}>
      {visibleSections >= 1 && skillsContent}
      {visibleSections >= 2 && passivesContent}
    </DetailRightPanel>
  )

  // Mobile tabs: Skills, Passives (no third tab for EGO)
  // Progressive rendering: show tabs when all sections loaded
  const mobileTabsContent =
    visibleSections >= totalSections ? (
      <>
        {/* Selector above tabs on mobile */}
        <div className="mb-4">{selector}</div>
        <MobileDetailTabs skillsContent={skillsContent} passivesContent={passivesContent} />
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

/**
 * EGODetailPage - EGO detail page with two-column layout
 *
 * Desktop: 4:6 ratio two-column grid
 * Mobile: Single column layout with tabs
 */
export default function EGODetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="ego" />}>
      <EGODetailContent />
    </Suspense>
  )
}
