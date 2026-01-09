import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { IdentityHeader } from '@/components/identity/IdentityHeader'
import { IdentityHeaderWithI18n } from '@/components/identity/IdentityHeaderI18n'
import { SkillsSectionI18n } from '@/components/identity/SkillI18n'
import { SkillTabButton } from '@/components/identity/SkillTabButton'
import { PassiveCardI18n } from '@/components/identity/PassiveI18n'
import { PanicTypeSectionI18n, SanityConditionsSectionI18n } from '@/components/identity/SanityI18n'
import { StatusPanel } from '@/components/identity/StatusPanel'
import { ResistancePanel } from '@/components/identity/ResistancePanel'
import { StaggerPanel } from '@/components/identity/StaggerPanel'
import { TraitsDisplay } from '@/components/identity/TraitsDisplay'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailEntitySelector } from '@/components/common/DetailEntitySelector'
import { DetailRightPanel } from '@/components/common/DetailRightPanel'
import { MobileDetailTabs } from '@/components/common/MobileDetailTabs'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { useIdentityDetailSpec } from '@/hooks/useIdentityDetailData'
import { MAX_LEVEL, MAX_ENTITY_TIER, PASSIVE_INDICATOR_COLORS } from '@/lib/constants'
import type { Uptie, IdentitySkillEntry } from '@/types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

// =============================================================================
// Main Content Component
// =============================================================================

/**
 * Inner content component that uses spec data only in shell.
 * I18n data is fetched in child components wrapped in Suspense.
 */
function IdentityDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation(['database', 'common'])
  const [activeSkillSlot, setActiveSkillSlot] = useState<SkillSlot>('skill1')

  // Controllable uptie and level state
  const [uptie, setUptie] = useState<number>(MAX_ENTITY_TIER.identity)
  const [level, setLevel] = useState<number>(MAX_LEVEL)

  // Route validation - id must be defined
  if (!id) {
    throw new Error('Identity ID is required')
  }

  // Spec data only - no language key, won't re-suspend on language change
  const identityData = useIdentityDetailSpec(id)

  // Cast to Uptie type for component props
  const uptieLevel = uptie as Uptie

  /**
   * Get effective passives at current uptie level.
   * Empty arrays mean "inherit from previous tier".
   */
  const getEffectivePassives = (passiveList: number[][], currentUptieIndex: number): number[] => {
    for (let i = currentUptieIndex; i >= 0; i--) {
      if (passiveList[i] && passiveList[i].length > 0) {
        return passiveList[i]
      }
    }
    return []
  }

  /**
   * Extract passive type info from ID.
   * ID format: {identity_id:5}{type:1}{variant:1}1
   * Type: 0=battle, 1=enhanced battle, 2=support
   */
  const getPassiveInfo = (passiveId: number): { type: number; variant: number } => {
    const suffix = passiveId % 100
    const type = Math.floor(suffix / 10)
    const variant = suffix % 10
    return { type, variant }
  }

  /**
   * Get locked passives: passives from higher tiers not available at current tier.
   * Excludes enhanced versions (type=1) if base version (type=0) with same variant exists.
   */
  const getLockedPassives = (
    passiveList: number[][],
    currentUptieIndex: number
  ): number[] => {
    const effectivePassives = new Set(getEffectivePassives(passiveList, currentUptieIndex))
    const lockedPassives: number[] = []
    const seenVariants = new Set<number>()

    // Collect variants of passives we already have (both base and enhanced)
    for (const passiveId of effectivePassives) {
      const { variant } = getPassiveInfo(passiveId)
      seenVariants.add(variant)
    }

    // Check higher tiers for new passives
    for (let i = currentUptieIndex + 1; i < passiveList.length; i++) {
      const tierPassives = passiveList[i]
      if (!tierPassives) continue

      for (const passiveId of tierPassives) {
        if (effectivePassives.has(passiveId)) continue

        const { type, variant } = getPassiveInfo(passiveId)

        // Skip enhanced versions (type=1) if we already have/shown the base variant
        if (type === 1 && seenVariants.has(variant)) continue

        // Skip if we've already added this variant as locked
        if (seenVariants.has(variant)) continue

        lockedPassives.push(passiveId)
        seenVariants.add(variant)
      }
    }

    return lockedPassives
  }

  // Get skill slot number for image paths
  const getSkillSlotNumber = (slot: SkillSlot): number => {
    switch (slot) {
      case 'skill1':
        return 1
      case 'skill2':
        return 2
      case 'skill3':
        return 3
      case 'skillDef':
        return 4
      default:
        return 1
    }
  }

  /**
   * Get attribute type for a skill slot, respecting uptie-level data merging.
   * Applies same merge logic as getMergedSkillData in SkillCard.tsx.
   */
  const getSkillAttributeType = (slot: SkillSlot): string | undefined => {
    const skillEntries: IdentitySkillEntry[] = identityData.skills[slot]
    if (!skillEntries || skillEntries.length === 0) return undefined

    // Get first skill entry (most identities have one entry per slot)
    const entry = skillEntries[0]
    if (!entry) return undefined

    // Merge skillData from index 0 to uptieLevel-1 (same as getMergedSkillData)
    // Later indices override earlier ones, so uptie 4 defense gets attributeType from skillData[3]
    const merged: Record<string, unknown> = {}
    for (let i = 0; i < uptieLevel; i++) {
      Object.assign(merged, entry.skillData[i])
    }

    return merged.attributeType as string | undefined
  }

  /**
   * Get condition for a passive, checking base version if enhanced doesn't have one.
   * Enhanced passives (type=1) inherit conditions from base (type=0) with same variant.
   */
  const getPassiveCondition = (passiveId: number) => {
    // First check if this passive has its own condition
    const directCondition = identityData.passives.conditions[String(passiveId)]
    if (directCondition) return directCondition

    // If not, check if it's an enhanced passive and look for base version's condition
    const { type } = getPassiveInfo(passiveId)
    if (type === 1) {
      // Build base passive ID: replace type digit (1) with 0
      const basePassiveId = passiveId - 10 // e.g., 1011411 -> 1011401
      return identityData.passives.conditions[String(basePassiveId)]
    }

    return undefined
  }

  // Calculate HP at current level
  const calculatedHp = Math.floor(identityData.hp.defaultStat + identityData.hp.incrementByLevel * level)
  const calculatedDefense = Math.max(1, level + identityData.defCorrection)

  // Get speed values at uptie level (0-indexed, so uptie 4 = index 3)
  const uptieIndex = uptieLevel - 1
  const minSpeed = identityData.minSpeedList[uptieIndex] ?? identityData.minSpeedList[0]
  const maxSpeed = identityData.maxSpeedList[uptieIndex] ?? identityData.maxSpeedList[0]

  // Selector component (shared between desktop and mobile)
  const selector = (
    <DetailEntitySelector
      entityType="identity"
      tier={uptie}
      onTierChange={setUptie}
      level={level}
      onLevelChange={setLevel}
      sticky
    />
  )

  // Left column: Header, Status, Resistance, Stagger, Traits (NO Sanity)
  const leftColumn = (
    <>
      {/* Header Area */}
      <div className="space-y-4">
        {/* Header with rank, name, and image - Suspends for i18n name */}
        <Suspense fallback={
          <IdentityHeader
            identityId={id}
            name=""
            rank={identityData.rank}
            uptie={uptie}
          />
        }>
          <IdentityHeaderWithI18n id={id} rank={identityData.rank} uptie={uptie} />
        </Suspense>

        {/* Three Horizontal Status Panels */}
        <div className="grid grid-cols-3 gap-2">
          <StatusPanel
            hp={calculatedHp}
            minSpeed={minSpeed}
            maxSpeed={maxSpeed}
            defLevel={calculatedDefense}
            defCorrection={identityData.defCorrection}
          />

          <ResistancePanel
            slash={identityData.ResistInfo.SLASH}
            pierce={identityData.ResistInfo.PENETRATE}
            blunt={identityData.ResistInfo.HIT}
          />

          <StaggerPanel maxHP={calculatedHp} staggerThresholds={identityData.staggerList} />
        </div>

        {/* Traits Panel - Already has granular Suspense internally */}
        <TraitsDisplay traits={identityData.unitKeywordList} />
      </div>
    </>
  )

  // Skill 3 is locked until uptie 3+
  const isSkill3Locked = uptie < 3

  // Skills content (shared between desktop and mobile)
  const skillsContent = (
    <div className="space-y-4">
      {/* Skill Selector */}
      <div className="flex gap-2">
        <SkillTabButton
          attributeType={getSkillAttributeType('skill1')}
          label={t('skill.skill1')}
          onClick={() => { setActiveSkillSlot('skill1'); }}
          isActive={activeSkillSlot === 'skill1'}
        />
        <SkillTabButton
          attributeType={getSkillAttributeType('skill2')}
          label={t('skill.skill2')}
          onClick={() => { setActiveSkillSlot('skill2'); }}
          isActive={activeSkillSlot === 'skill2'}
        />
        <SkillTabButton
          attributeType={getSkillAttributeType('skill3')}
          label={t('skill.skill3')}
          onClick={() => { setActiveSkillSlot('skill3'); }}
          isActive={activeSkillSlot === 'skill3'}
          isLocked={isSkill3Locked}
        />
        <SkillTabButton
          attributeType={getSkillAttributeType('skillDef')}
          label={t('skill.defense')}
          onClick={() => { setActiveSkillSlot('skillDef'); }}
          isActive={activeSkillSlot === 'skillDef'}
        />
      </div>

      {/* Skill Display - uses internal granular Suspense for name/description */}
      <SkillsSectionI18n
        id={id}
        skills={identityData.skills}
        activeSkillSlot={activeSkillSlot}
        uptieLevel={uptieLevel}
        isSkill3Locked={isSkill3Locked}
        getSkillSlotNumber={getSkillSlotNumber}
      />
    </div>
  )

  // Get effective and locked passives for current uptie
  const effectiveBattlePassives = getEffectivePassives(identityData.passives.battlePassiveList, uptieIndex)
  const lockedBattlePassives = getLockedPassives(identityData.passives.battlePassiveList, uptieIndex)
  const effectiveSupportPassives = getEffectivePassives(identityData.passives.supportPassiveList, uptieIndex)
  const lockedSupportPassives = getLockedPassives(identityData.passives.supportPassiveList, uptieIndex)

  // Passives content - PassiveCardI18n uses internal granular Suspense
  const passivesContent = (
    <div className="border rounded p-4 space-y-4">
      {/* Battle Passive Section */}
      <div className="space-y-3">
        <div className="mb-4">
          <span
            className="font-bold px-8 py-1 text-md"
            style={{ color: PASSIVE_INDICATOR_COLORS.TEXT, border: `2px solid ${PASSIVE_INDICATOR_COLORS.BORDER}` }}
          >
            {t('passive.battle')}
          </span>
        </div>
        {effectiveBattlePassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passiveId)}
            isLocked={false}
          />
        ))}
        {lockedBattlePassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passiveId)}
            isLocked={true}
          />
        ))}
        {effectiveBattlePassives.length === 0 && lockedBattlePassives.length === 0 && (
          <div className="text-sm text-muted-foreground">No battle passives</div>
        )}
      </div>

      {/* Support Passive Section */}
      <div className="space-y-3">
        <div className="mb-4 mt-8">
          <span
            className="font-bold px-8 py-1 text-md"
            style={{ color: PASSIVE_INDICATOR_COLORS.TEXT, border: `2px solid ${PASSIVE_INDICATOR_COLORS.BORDER}` }}
          >
            {t('passive.support')}
          </span>
        </div>
        {effectiveSupportPassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passiveId)}
            isLocked={false}
          />
        ))}
        {lockedSupportPassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passiveId)}
            isLocked={true}
          />
        ))}
        {effectiveSupportPassives.length === 0 && lockedSupportPassives.length === 0 && (
          <div className="text-sm text-muted-foreground">No support passives</div>
        )}
      </div>
    </div>
  )

  // Sanity content (moved to right column, also used in mobile tabs)
  // Components use internal granular Suspense - no outer wrapper needed
  const sanityContent = (
    <div className="border rounded p-4 space-y-4">
      {/* Panic Type - uses internal Suspense for name/description */}
      <PanicTypeSectionI18n panicType={identityData.panicType} />

      {/* Sanity Conditions - uses internal Suspense for condition text */}
      <SanityConditionsSectionI18n
        addConditions={identityData.mentalConditionInfo.add}
        minConditions={identityData.mentalConditionInfo.min}
      />
    </div>
  )

  // Desktop right column: Selector (sticky) + Skills + Passives + Sanity
  const rightColumn = (
    <DetailRightPanel selector={selector}>
      {skillsContent}
      {passivesContent}
      {sanityContent}
    </DetailRightPanel>
  )

  // Mobile tabs: Skills, Passives, Sanity (Info is shown above via leftColumn)
  const mobileTabsContent = (
    <>
      {/* Selector above tabs on mobile */}
      <div className="mb-4">{selector}</div>
      <MobileDetailTabs
        skillsContent={skillsContent}
        passivesContent={passivesContent}
        thirdTabContent={sanityContent}
      />
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
