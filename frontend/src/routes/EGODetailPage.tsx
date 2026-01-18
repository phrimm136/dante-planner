import { useParams } from '@tanstack/react-router'
import { Suspense, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { EGOHeader } from '@/components/ego/EGOHeader'
import { EGOHeaderWithI18n } from '@/components/ego/EGOHeaderI18n'
import { SinCostPanel } from '@/components/ego/SinCostPanel'
import { SinResistancePanel } from '@/components/ego/SinResistancePanel'
import { SkillsSectionI18n } from '@/components/ego/SkillI18n'
import { PassiveCardWithSuspense } from '@/components/ego/PassiveI18n'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { EntityMetaInfo } from '@/components/common/EntityMetaInfo'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { DetailEntitySelector } from '@/components/common/DetailEntitySelector'
import { DetailRightPanel } from '@/components/common/DetailRightPanel'
import { MobileDetailTabs } from '@/components/common/MobileDetailTabs'
import { SkillTabButton } from '@/components/identity/SkillTabButton'
import { useEGODetailSpec } from '@/hooks/useEGODetailData'
import { MAX_ENTITY_TIER } from '@/lib/constants'
import type { Threadspin } from '@/types/EGOTypes'

type SkillType = 'awaken' | 'erosion'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGODetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation(['database', 'common'])
  const [skillType, setSkillType] = useState<SkillType>('awaken')

  // Controllable threadspin state
  const [threadspin, setThreadspin] = useState<number>(MAX_ENTITY_TIER.ego)

  // Progressive rendering: render sections one-by-one
  // Sections: 1=Skills, 2=Passives
  const [visibleSections, setVisibleSections] = useState(0)
  const totalSections = 2

  // Progressively show more sections (start immediately)
  useEffect(() => {
    if (visibleSections < totalSections) {
      const rafId = requestAnimationFrame(() => {
        setVisibleSections((prev) => prev + 1)
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [visibleSections])

  // Route validation - id must be defined
  if (!id) {
    throw new Error('EGO ID is required')
  }

  // Spec data only - no language key, won't re-suspend on language change
  const spec = useEGODetailSpec(id)

  // Cast to Threadspin type for component props
  const threadspinLevel = threadspin as Threadspin

  /**
   * Get effective passives at current threadspin level.
   * Empty arrays mean "inherit from previous tier".
   */
  const getEffectivePassives = (passiveList: string[][], currentThreadspinIndex: number): string[] => {
    for (let i = currentThreadspinIndex; i >= 0; i--) {
      if (passiveList[i] && passiveList[i].length > 0) {
        return passiveList[i]
      }
    }
    return []
  }

  /**
   * Get locked passives: passives from higher tiers not available at current tier.
   * SIMPLIFIED from Identity - no variant filtering, just show all higher-tier passives.
   */
  const getLockedPassives = (
    passiveList: string[][],
    currentThreadspinIndex: number
  ): string[] => {
    const effectiveSet = new Set(getEffectivePassives(passiveList, currentThreadspinIndex))
    const locked: string[] = []

    for (let i = currentThreadspinIndex + 1; i < passiveList.length; i++) {
      const tierPassives = passiveList[i]
      if (!tierPassives) continue

      for (const passiveId of tierPassives) {
        if (!effectiveSet.has(passiveId)) {
          locked.push(passiveId)
        }
      }
    }

    return locked
  }

  // Check if erosion skills exist
  const hasErosion = spec.skills.erosion && spec.skills.erosion.length > 0

  // Threadspin index for passive arrays (0-indexed)
  const threadspinIndex = threadspinLevel - 1

  // Get effective and locked passives for current threadspin
  const effectivePassives = getEffectivePassives(spec.passives.passiveList, threadspinIndex)
  const lockedPassives = getLockedPassives(spec.passives.passiveList, threadspinIndex)

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

        {/* Two Horizontal Panels: Sin Cost and Sin Resistance */}
        <div className="grid grid-cols-2 gap-2">
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
