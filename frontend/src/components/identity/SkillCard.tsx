import { SkillImageComposite } from './SkillImageComposite'
import { SkillInfoPanel } from './SkillInfoPanel'
import { SkillDescription } from './SkillDescription'
import { SkillCardLayout } from '@/components/common/SkillCardLayout'
import type {
  IdentitySkillEntry,
  IdentitySkillI18n,
  IdentitySkillDataEntry,
  IdentitySkillDescEntry,
  Uptie
} from '@/types/IdentityTypes'

interface SkillCardProps {
  identityId: string
  skillSlot: number
  variantIndex: number
  skillEntry: IdentitySkillEntry
  skillI18n: IdentitySkillI18n
  uptie: Uptie
}

/**
 * Get merged skill data for a specific uptie level.
 * Earlier uptie levels provide base values, later levels override.
 */
function getMergedSkillData(
  skillData: IdentitySkillEntry['skillData'],
  uptie: Uptie
): IdentitySkillDataEntry {
  const merged: IdentitySkillDataEntry = {}
  // Merge all entries from 0 up to the selected uptie (0-indexed, so uptie 1 = index 0)
  for (let i = 0; i < uptie; i++) {
    Object.assign(merged, skillData[i])
  }
  return merged
}

/**
 * Get skill description for a specific uptie level.
 */
function getSkillDesc(
  descs: IdentitySkillDescEntry[],
  uptie: Uptie
): IdentitySkillDescEntry {
  // uptie 1-4 maps to descs[0-3]
  return descs[uptie - 1] || {}
}

/**
 * Derive coin string from coinDescs array.
 * Each coin is 'C' (normal) or 'U' (unbreakable/super coin).
 * Super coins are identified by [SuperCoin] in the description.
 */
function getCoinString(coinDescs: string[] | undefined): string {
  if (!coinDescs || coinDescs.length === 0) return ''
  return coinDescs
    .map((desc) => (desc.includes('[SuperCoin]') ? 'U' : 'C'))
    .join('')
}

/**
 * SkillCard - Complete skill display card
 *
 * Layout:
 * - Horizontal layout with skill image on left, info panel on right
 * - Skill description below the main layout
 */
export function SkillCard({
  identityId,
  skillSlot,
  variantIndex,
  skillEntry,
  skillI18n,
  uptie,
}: SkillCardProps) {
  const mergedData = getMergedSkillData(skillEntry.skillData, uptie)
  const isDefenseSkill = mergedData.atkType === 'NONE' || !mergedData.atkType
  const skillName = skillI18n.name
  const skillDescData = getSkillDesc(skillI18n.descs, uptie)
  const coinString = getCoinString(skillDescData.coinDescs)

  return (
    <SkillCardLayout
      imageComposite={
        <SkillImageComposite
          identityId={identityId}
          skillSlot={skillSlot}
          variantIndex={variantIndex}
          skillData={mergedData}
          uptie={uptie}
        />
      }
      infoPanel={
        <SkillInfoPanel
          skillName={skillName}
          skillData={mergedData}
          coinString={coinString}
          isDefenseSkill={isDefenseSkill}
        />
      }
      description={<SkillDescription descData={skillDescData} />}
    />
  )
}
