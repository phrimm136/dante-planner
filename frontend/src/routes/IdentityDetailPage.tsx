import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { IdentityHeader } from '@/components/identity/IdentityHeader'
import { StatusPanel } from '@/components/identity/StatusPanel'
import { ResistancePanel } from '@/components/identity/ResistancePanel'
import { StaggerPanel } from '@/components/identity/StaggerPanel'
import { TraitsDisplay } from '@/components/identity/TraitsDisplay'
import { SkillCard } from '@/components/identity/SkillCard'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailEntitySelector } from '@/components/common/DetailEntitySelector'
import { DetailRightPanel } from '@/components/common/DetailRightPanel'
import { MobileDetailTabs } from '@/components/common/MobileDetailTabs'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { FormattedSanityText } from '@/components/common/FormattedSanityText'
import { StyledSkillName } from '@/components/common/StyledSkillName'
import { useIdentityDetailData } from '@/hooks/useIdentityDetailData'
import { usePanicInfo, getPanicEntry } from '@/hooks/usePanicInfo'
import { useSanityConditionFormatter } from '@/lib/sanityConditionFormatter'
import { cn } from '@/lib/utils'
import { getPanicIconPath, getAffinityIconPath } from '@/lib/assetPaths'
import { MAX_LEVEL, MAX_ENTITY_TIER, SANITY_INDICATOR_COLORS, SANITY_CONDITION_TYPE, PASSIVE_INDICATOR_COLORS } from '@/lib/constants'
import type { Uptie } from '@/types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function IdentityDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation()
  const [activeSkillSlot, setActiveSkillSlot] = useState<SkillSlot>('skill1')

  // Controllable uptie and level state
  const [uptie, setUptie] = useState<number>(MAX_ENTITY_TIER.identity)
  const [level, setLevel] = useState<number>(MAX_LEVEL)

  // Route validation - id must be defined
  if (!id) {
    throw new Error('Identity ID is required')
  }

  // Hooks must be called unconditionally - route should validate id exists
  const { spec: identityData, i18n: identityI18n } = useIdentityDetailData(id)
  const { data: panicInfo } = usePanicInfo()
  const { formatAll: formatSanityConditions } = useSanityConditionFormatter()

  // Get panic entry for this identity
  const panicEntry = getPanicEntry(panicInfo, identityData.panicType)

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
        {/* Header with rank, name, and image */}
        <IdentityHeader
          identityId={id}
          name={identityI18n.name}
          rank={identityData.rank}
          uptie={uptie}
        />

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

        {/* Traits Panel */}
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
        <button
          onClick={() => { setActiveSkillSlot('skill1'); }}
          className={cn(
            'flex-1 py-2 px-4 rounded',
            activeSkillSlot === 'skill1'
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted'
          )}
        >
          Skill 1
        </button>
        <button
          onClick={() => { setActiveSkillSlot('skill2'); }}
          className={cn(
            'flex-1 py-2 px-4 rounded',
            activeSkillSlot === 'skill2'
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted'
          )}
        >
          Skill 2
        </button>
        <button
          onClick={() => { setActiveSkillSlot('skill3'); }}
          className={cn(
            'flex-1 py-2 px-4 rounded',
            activeSkillSlot === 'skill3'
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted',
            isSkill3Locked && 'opacity-50'
          )}
        >
          Skill 3
          {isSkill3Locked && <span className="ml-1 text-xs">🔒</span>}
        </button>
        <button
          onClick={() => { setActiveSkillSlot('skillDef'); }}
          className={cn(
            'flex-1 py-2 px-4 rounded',
            activeSkillSlot === 'skillDef'
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted'
          )}
        >
          Defense
        </button>
      </div>

      {/* Skill Display - Show ALL skills in the selected slot in one container */}
      <div className={cn(
        'border rounded divide-y',
        activeSkillSlot === 'skill3' && isSkill3Locked && 'opacity-50'
      )}>
        {identityData.skills[activeSkillSlot].map((skill, idx) => {
          // Get skill i18n by skill ID
          const skillI18n = identityI18n.skills[String(skill.id)]

          // For locked Skill 3, show tier 3 data so users can preview what they'll unlock
          const displayUptie = (activeSkillSlot === 'skill3' && isSkill3Locked) ? 3 as Uptie : uptieLevel

          return (
            <SkillCard
              key={idx}
              identityId={id}
              skillSlot={getSkillSlotNumber(activeSkillSlot)}
              variantIndex={idx}
              skillEntry={skill}
              skillI18n={skillI18n}
              uptie={displayUptie}
            />
          )
        })}
      </div>
    </div>
  )

  // Get effective and locked passives for current uptie
  const effectiveBattlePassives = getEffectivePassives(identityData.passives.battlePassiveList, uptieIndex)
  const lockedBattlePassives = getLockedPassives(identityData.passives.battlePassiveList, uptieIndex)
  const effectiveSupportPassives = getEffectivePassives(identityData.passives.supportPassiveList, uptieIndex)
  const lockedSupportPassives = getLockedPassives(identityData.passives.supportPassiveList, uptieIndex)

  /**
   * Get condition for a passive, checking base version if enhanced doesn't have one.
   * Enhanced passives (type=1) inherit conditions from base (type=0) with same variant.
   */
  const getPassiveCondition = (passiveId: number) => {
    // First check if this passive has its own condition
    const directCondition = identityData.passives.conditions[String(passiveId)]
    if (directCondition) return directCondition

    // If not, check if it's an enhanced passive and look for base version's condition
    const { type, variant } = getPassiveInfo(passiveId)
    if (type === 1) {
      // Build base passive ID: replace type digit (1) with 0
      const basePassiveId = passiveId - 10 // e.g., 1011411 -> 1011401
      return identityData.passives.conditions[String(basePassiveId)]
    }

    return undefined
  }

  // Helper to render a passive entry (no container, just content)
  const renderPassiveCard = (passiveId: number, isLocked: boolean) => {
    const passiveI18n = identityI18n.passives[String(passiveId)]
    const condition = getPassiveCondition(passiveId)

    return (
      <div
        key={passiveId}
        className={cn(
          'space-y-1',
          isLocked && 'opacity-50'
        )}
      >
        <div className="flex items-center gap-2">
          <StyledSkillName
            name={passiveI18n?.name || `Passive ${String(passiveId)}`}
            attributeType="NEUTRAL"
          />
          {isLocked && <span className="text-xs">🔒</span>}
        </div>
        {condition && (
          <div className="flex items-center gap-1 text-md ml-1">
            {Object.entries(condition.values).map(([affinity, count], idx) => (
              <span key={affinity} className="flex items-center gap-0.5">
                {idx > 0 && <span className="mx-1">+</span>}
                <img
                  src={getAffinityIconPath(affinity)}
                  alt={affinity}
                  className="w-8 h-8"
                />
                <span>x{count}</span>
              </span>
            ))}
            <span className="ml-1">{t(`passive.${condition.type.toLowerCase()}`)}</span>
          </div>
        )}
        <div className="text-sm">
          <FormattedDescription text={passiveI18n?.desc || ''} />
        </div>
      </div>
    )
  }

  // Passives content (shared between desktop and mobile)
  const passivesContent = (
    <div className="border rounded p-4 space-y-4">
      <div className="font-semibold">Passives</div>

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
        {/* Active passives */}
        {effectiveBattlePassives.map((passiveId) => renderPassiveCard(passiveId, false))}
        {/* Locked passives (from higher tiers) */}
        {lockedBattlePassives.map((passiveId) => renderPassiveCard(passiveId, true))}
        {/* Empty state */}
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
        {/* Active passives */}
        {effectiveSupportPassives.map((passiveId) => renderPassiveCard(passiveId, false))}
        {/* Locked passives (from higher tiers) */}
        {lockedSupportPassives.map((passiveId) => renderPassiveCard(passiveId, true))}
        {/* Empty state */}
        {effectiveSupportPassives.length === 0 && lockedSupportPassives.length === 0 && (
          <div className="text-sm text-muted-foreground">No support passives</div>
        )}
      </div>
    </div>
  )

  // Sanity content (moved to right column, also used in mobile tabs)
  const sanityContent = (
    <div className="border rounded p-4 space-y-4">
      <div className="font-semibold">{t('sanity.title', 'Sanity')}</div>

      {/* Panic Type */}
      {panicEntry && (
        <div>
          <div className="mb-2">
            <span
              className="font-bold px-3 py-1 text-sm"
              style={{ color: SANITY_INDICATOR_COLORS.INCREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}` }}
            >
              {t('sanity.panicType', 'Panic Type')}
            </span>
          </div>
          <div className="flex gap-3">
            <div className="flex flex-col items-center">
              <img
                src={getPanicIconPath(identityData.panicType)}
                alt={panicEntry.name}
                className="w-16 h-16 object-contain"
              />
              <div className="font-semibold text-sm mt-1">{panicEntry.name}</div>
            </div>
            <div className="flex-1 text-sm">
              <div>
                <span>·{t('sanity.panicEffect', 'Panic Effect')}</span>
              </div>
              <div>
                <FormattedDescription text={panicEntry.panicDesc} />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Sanity Increment Section */}
      <div>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.INCREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}` }}
          >
            {t('sanity.increaseHeader', 'Factors increasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1">
          {identityData.mentalConditionInfo.add.length > 0
            ? formatSanityConditions(identityData.mentalConditionInfo.add, SANITY_CONDITION_TYPE.INCREMENT).map((desc, idx) => (
                <div key={idx}>
                  <span>·</span>
                  <FormattedSanityText text={desc} />
                </div>
              ))
            : <div className="text-muted-foreground">{t('sanity.noIncrease', 'No sanity increase conditions')}</div>}
        </div>
      </div>

      {/* Sanity Decrement Section */}
      <div>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.DECREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.DECREMENT_BORDER}` }}
          >
            {t('sanity.decreaseHeader', 'Factors decreasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1">
          {identityData.mentalConditionInfo.min.length > 0
            ? formatSanityConditions(identityData.mentalConditionInfo.min, SANITY_CONDITION_TYPE.DECREMENT).map((desc, idx) => (
                <div key={idx}>
                  <span>·</span>
                  <FormattedSanityText text={desc} />
                </div>
              ))
            : <div className="text-muted-foreground">{t('sanity.noDecrease', 'No sanity decrease conditions')}</div>}
        </div>
      </div>
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
        sanityContent={sanityContent}
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

/**
 * IdentityDetailPage - Identity detail page with two-column layout
 *
 * Desktop: 4:6 ratio with sticky selector in right column
 * Mobile: Info at top, then tabbed content (Skills/Passives/Sanity)
 */
export default function IdentityDetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="identity" />}>
      <IdentityDetailContent />
    </Suspense>
  )
}
