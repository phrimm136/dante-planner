import { EGOSkillImageComposite } from './EGOSkillImageComposite'
import { EGOSkillInfoPanel } from './EGOSkillInfoPanel'
import { SkillDescription } from '@/components/identity/SkillDescription'
import { SkillCardLayout } from '@/components/common/SkillCardLayout'
import type {
  EGOSkillEntry,
  EGOSkillI18n,
  EGOSkillDataEntry,
  EGOSkillDescEntry,
  Threadspin
} from '@/types/EGOTypes'

interface EGOSkillCardProps {
  egoId: string
  skillType: 'awaken' | 'erosion'
  skillEntry: EGOSkillEntry
  skillI18n: EGOSkillI18n
  threadspin: Threadspin
}

/**
 * Get merged skill data for a specific threadspin level.
 * Earlier threadspin levels provide base values, later levels override.
 */
function getMergedSkillData(
  skillData: EGOSkillEntry['skillData'],
  threadspin: Threadspin
): EGOSkillDataEntry {
  const merged: EGOSkillDataEntry = {}
  // Merge all entries from 0 up to the selected threadspin (0-indexed, so threadspin 1 = index 0)
  for (let i = 0; i < threadspin; i++) {
    Object.assign(merged, skillData[i])
  }
  return merged
}

/**
 * Get skill description for a specific threadspin level.
 */
function getSkillDesc(
  descs: EGOSkillDescEntry[],
  threadspin: Threadspin
): EGOSkillDescEntry {
  // threadspin 1-4 maps to descs[0-3]
  return descs[threadspin - 1] || {}
}

/**
 * Derive coin string from coinDescs array.
 * Each coin is 'C' (normal) or 'U' (unbreakable/super coin).
 */
function getCoinString(coinDescs: string[] | undefined): string {
  if (!coinDescs || coinDescs.length === 0) return ''
  return coinDescs
    .map((desc) => (desc.includes('[SuperCoin]') ? 'U' : 'C'))
    .join('')
}

/**
 * EGOSkillCard - Complete EGO skill display card
 *
 * Layout:
 * - Horizontal layout with skill image on left, info panel on right
 * - Skill description below the main layout
 */
export function EGOSkillCard({
  egoId,
  skillType,
  skillEntry,
  skillI18n,
  threadspin,
}: EGOSkillCardProps) {
  const mergedData = getMergedSkillData(skillEntry.skillData, threadspin)
  const skillName = skillI18n.name
  const skillDescData = getSkillDesc(skillI18n.descs, threadspin)
  const coinString = getCoinString(skillDescData.coinDescs)

  return (
    <SkillCardLayout
      imageComposite={
        <EGOSkillImageComposite
          egoId={egoId}
          skillType={skillType}
          skillData={mergedData}
        />
      }
      infoPanel={
        <EGOSkillInfoPanel
          skillName={skillName}
          skillData={mergedData}
          coinString={coinString}
        />
      }
      description={<SkillDescription descData={skillDescData} />}
    />
  )
}
