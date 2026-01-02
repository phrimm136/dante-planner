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
import { LoadingState } from '@/components/common/LoadingState'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { StyledSkillName } from '@/components/common/StyledSkillName'
import { useIdentityDetailData } from '@/hooks/useIdentityDetailData'
import { useSanityConditionFormatter } from '@/lib/sanityConditionFormatter'
import { cn } from '@/lib/utils'
import { MAX_LEVEL, MAX_ENTITY_TIER, SANITY_INDICATOR_COLORS, SANITY_CONDITION_TYPE } from '@/lib/constants'
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

  // Hooks must be called unconditionally - route should validate id exists
  const { spec: identityData, i18n: identityI18n } = useIdentityDetailData(id)
  const { formatAll: formatSanityConditions } = useSanityConditionFormatter()

  // Cast to Uptie type for component props
  const uptieLevel = uptie as Uptie

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
              : 'bg-muted'
          )}
        >
          Skill 3
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

      {/* Skill Display - Show ALL skills in the selected slot */}
      <div className="space-y-4">
        {identityData.skills[activeSkillSlot].map((skill, idx) => {
          // Get skill i18n by skill ID
          const skillI18n = identityI18n.skills[String(skill.id)]

          return (
            <SkillCard
              key={idx}
              identityId={id}
              skillSlot={getSkillSlotNumber(activeSkillSlot)}
              variantIndex={idx}
              skillEntry={skill}
              skillI18n={skillI18n}
              uptie={uptieLevel}
            />
          )
        })}
      </div>
    </div>
  )

  // Passives content (shared between desktop and mobile)
  const passivesContent = (
    <div className="border rounded p-4 space-y-4">
      <div className="font-semibold">Passives</div>

      {/* Battle Passive Section */}
      <div className="space-y-3">
        <div className="text-sm font-medium">Battle Passives</div>
        {/* Get active passives for current uptie level */}
        {identityData.passives.battlePassiveList[uptieIndex]?.map((passiveId) => {
          const passiveI18n = identityI18n.passives[String(passiveId)]
          const condition = identityData.passives.conditions[String(passiveId)]

          return (
            <div key={passiveId} className="border rounded p-3 space-y-2">
              <StyledSkillName
                name={passiveI18n.name || `Passive ${String(passiveId)}`}
                attributeType="NEUTRAL"
              />
              {condition && (
                <div className="text-xs">
                  {condition.type}: {Object.entries(condition.values).map(([key, val]) => `${key} x${val}`).join(', ')}
                </div>
              )}
              <div className="text-sm">
                <FormattedDescription text={passiveI18n.desc || ''} />
              </div>
            </div>
          )
        })}
        {(!identityData.passives.battlePassiveList[uptieIndex] || identityData.passives.battlePassiveList[uptieIndex].length === 0) && (
          <div className="text-sm text-muted-foreground">No battle passives at this uptie level</div>
        )}
      </div>

      {/* Support Passive Section */}
      <div className="space-y-3">
        <div className="text-sm font-medium">Support Passives</div>
        {identityData.passives.supportPassiveList[uptieIndex]?.map((passiveId) => {
          const passiveI18n = identityI18n.passives[String(passiveId)]
          const condition = identityData.passives.conditions[String(passiveId)]

          return (
            <div key={passiveId} className="border rounded p-3 space-y-2">
              <StyledSkillName
                name={passiveI18n.name || `Support Passive ${String(passiveId)}`}
                attributeType="NEUTRAL"
              />
              {condition && (
                <div className="text-xs">
                  {condition.type}: {Object.entries(condition.values).map(([key, val]) => `${key} x${val}`).join(', ')}
                </div>
              )}
              <div className="text-sm">
                <FormattedDescription text={passiveI18n.desc || ''} />
              </div>
            </div>
          )
        })}
        {(!identityData.passives.supportPassiveList[uptieIndex] || identityData.passives.supportPassiveList[uptieIndex].length === 0) && (
          <div className="text-sm text-muted-foreground">No support passives at this uptie level</div>
        )}
      </div>
    </div>
  )

  // Sanity content (moved to right column, also used in mobile tabs)
  const sanityContent = (
    <div className="border rounded p-4 space-y-4">
      <div className="font-semibold">{t('sanity.title', 'Sanity')}</div>

      {/* Panic Type */}
      <div className="text-sm">
        <span className="font-medium">{t('sanity.panicType', 'Panic Type')}:</span>{' '}
        <span className="text-muted-foreground">Type {identityData.panicType}</span>
      </div>

      {/* Sanity Increment Section */}
      <div style={{ color: SANITY_INDICATOR_COLORS.INCREMENT }}>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}` }}
          >
            {t('sanity.increaseHeader', 'Factors increasing Sanity')}
          </span>
        </div>
        <ul className="list-disc list-inside text-sm space-y-1 ml-1">
          {identityData.mentalConditionInfo.add.length > 0
            ? formatSanityConditions(identityData.mentalConditionInfo.add, SANITY_CONDITION_TYPE.INCREMENT).map((desc, idx) => (
                <li key={idx}>{desc}</li>
              ))
            : <li className="text-muted-foreground">{t('sanity.noIncrease', 'No sanity increase conditions')}</li>}
        </ul>
      </div>

      {/* Sanity Decrement Section */}
      <div style={{ color: SANITY_INDICATOR_COLORS.DECREMENT }}>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ border: `2px solid ${SANITY_INDICATOR_COLORS.DECREMENT_BORDER}` }}
          >
            {t('sanity.decreaseHeader', 'Factors decreasing Sanity')}
          </span>
        </div>
        <ul className="list-disc list-inside text-sm space-y-1 ml-1">
          {identityData.mentalConditionInfo.min.length > 0
            ? formatSanityConditions(identityData.mentalConditionInfo.min, SANITY_CONDITION_TYPE.DECREMENT).map((desc, idx) => (
                <li key={idx}>{desc}</li>
              ))
            : <li className="text-muted-foreground">{t('sanity.noDecrease', 'No sanity decrease conditions')}</li>}
        </ul>
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
    <Suspense fallback={<LoadingState message="Loading identity..." />}>
      <IdentityDetailContent />
    </Suspense>
  )
}
