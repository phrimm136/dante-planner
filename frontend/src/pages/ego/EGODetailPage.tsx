import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  EGOHeader,
  EGOHeaderWithI18n,
  SinCostPanel,
  SinResistancePanel,
  SkillsSectionI18n,
  PassiveCardWithSuspense,
} from '@/pages/ego'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { EntityMetaInfo } from '@/components/layout/EntityMetaInfo'
import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { DetailRightPanel } from '@/components/layout/DetailRightPanel'
import { MobileDetailTabs } from '@/components/layout/MobileDetailTabs'
import { SkillTabButton } from '@/pages/identity'
import { useEGODetailSpec } from '@/pages/ego'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEffectiveEgoPassives, getLockedEgoPassives } from './lib/egoPassiveSelection'
import type { Threadspin } from '@/pages/ego'

type SkillType = 'awaken' | 'erosion'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGODetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation(['database', 'common'])
  const [skillType, setSkillType] = useState<SkillType>('awaken')

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


  // Check if erosion skills exist
  const hasErosion = spec.skills.erosion && spec.skills.erosion.length > 0

  // Threadspin index for passive arrays (0-indexed)
  const threadspinIndex = threadspinLevel - 1

  // Get effective and locked passives for current threadspin
  const effectivePassives = getEffectiveEgoPassives(spec.passives.passiveList, threadspinIndex)
  const lockedPassives = getLockedEgoPassives(spec.passives.passiveList, threadspinIndex)

  // Get attribute type for skill type tab (first skill's first data entry)
  const getSkillAttributeType = (skillTypeKey: SkillType): string | undefined => {
    const skills = spec.skills[skillTypeKey]
    if (!skills || skills.length === 0) return undefined

    for (const entry of skills) {
      for (const data of entry.skillData) {
        if (data.attributeType) {
          return data.attributeType
        }
      }
    }
    return undefined
  }

  // Selector component (shared between desktop and mobile)
  const selector = (
    <DetailEntitySelector
      entityType="ego"
      tier={threadspin}
      onTierChange={setThreadspin}
      maxTier={spec.maxThreadspin}
      sticky
    />
  )

  // Left column: Header (with i18n), Sin Cost, Sin Resistance
  const leftColumn = (
    <>
      <div className="space-y-4">
        {/* Header with rank, name, and image - Suspends for i18n name */}
        <Suspense fallback={
          <EGOHeader
            egoId={id}
            name=""
            rank={spec.egoType}
          />
        }>
          <EGOHeaderWithI18n id={id} rank={spec.egoType} />
        </Suspense>

        {/* Sin Cost and Sin Resistance Panels */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          <SinCostPanel costs={spec.requirements} />
          <SinResistancePanel resistances={spec.attributeResist} />
        </div>

        {/* Season and Release Date - Suspense for i18n data */}
        <Suspense fallback={
          <div className="grid grid-cols-2 gap-2">
            <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
            <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
          </div>
        }>
          <EntityMetaInfo season={spec.season} updateDate={spec.updatedDate} />
        </Suspense>
      </div>
    </>
  )

  // Skills content (shared between desktop and mobile)
  const skillsContent = (
    <div className="space-y-4">
      {/* Skill Type Selector */}
      <div className="flex gap-2">
        <SkillTabButton
          attributeType={getSkillAttributeType('awaken')}
          label={t('skill.awakening')}
          onClick={() => { setSkillType('awaken'); }}
          isActive={skillType === 'awaken'}
        />
{hasErosion && (
          <SkillTabButton
            attributeType={getSkillAttributeType('erosion')}
            label={t('skill.corrosion')}
            onClick={() => { setSkillType('erosion'); }}
            isActive={skillType === 'erosion'}
          />
        )}
      </div>

      {/* Skill Display - uses internal granular Suspense for name/description */}
      <SkillsSectionI18n
        egoId={id}
        skillType={skillType}
        skills={spec.skills[skillType]}
        threadspin={threadspinLevel}
      />
    </div>
  )

  // Passives content - PassiveCardWithSuspense uses internal granular Suspense
  const passivesContent = (
    <div className="border rounded p-4 space-y-4">
      <div className="space-y-3">
        {effectivePassives.map((passiveId) => (
          <PassiveCardWithSuspense
            key={passiveId}
            id={id}
            passiveId={passiveId}
            isLocked={false}
          />
        ))}
        {lockedPassives.map((passiveId) => (
          <PassiveCardWithSuspense
            key={passiveId}
            id={id}
            passiveId={passiveId}
            isLocked={true}
          />
        ))}
        {effectivePassives.length === 0 && lockedPassives.length === 0 && (
          <div className="text-sm text-muted-foreground">{t('passive.none', 'No passives')}</div>
        )}
      </div>
    </div>
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
  const mobileTabsContent = visibleSections >= totalSections ? (
    <>
      {/* Selector above tabs on mobile */}
      <div className="mb-4">{selector}</div>
      <MobileDetailTabs
        skillsContent={skillsContent}
        passivesContent={passivesContent}
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
