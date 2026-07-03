import { useState } from 'react'

import { getLockIconPath } from '@/shared/assets'
import { type SkillAttributeType, type SkillDescEntry } from '@/shared/gameData'
import { SkillImageComposite } from './SkillImageComposite'
import { SkillCardLayout } from './SkillCardLayout'
import { SkillInfoPanelWithSuspense } from './SkillInfoPanel'
import { SkillDescriptionWithSuspense } from './SkillDescription'

/** Minimal structural shape of the per-level skill stats a card reads. */
interface SkillCardData {
  attributeType?: string
  atkType?: string
  defaultValue?: number
  scale?: number
  skillLevelCorrection?: number
  targetNum?: number
}

/** Combined per-entity detail-i18n shape needed by the name + description slots. */
interface SkillDetailI18n {
  skills: Record<string, { name?: string; descs?: SkillDescEntry[]; flavor?: string } | undefined>
}

interface SkillCardProps {
  entityId: string
  /** i18n key for name + description (identity uses textID, ego uses id). */
  skillId: number
  /** Enhancement level — identity uptie (1–4) or ego threadspin (1–5). */
  level: number
  skillData: SkillCardData
  coinString: string
  /** Domain-resolved skill image path (identity id/iconID, ego awaken/erosion). */
  skillImagePath: string
  /** Frame tier (identity per-skill, ego fixed at 3). */
  skillTier: number
  /**
   * The owning slice's detail-i18n hook, injected so this shared card stays
   * free of any `@/pages/*` import (sink rule). Identity passes
   * `useIdentityDetailI18n`; ego passes `useEGODetailI18n`.
   */
  useDetailI18n: (id: string) => SkillDetailI18n
  /** Identity-only: renders the defense level icon. EGO skills are always attack. */
  isDefenseSkill?: boolean
  /** EGO-only: sanity (MP) cost. When provided, renders the sanity-cost stat. */
  sanityCost?: number
  /** Identity-only: dims the card and overlays a lock icon. */
  isLocked?: boolean
}

/**
 * SkillCard — the single skill-card surface shared by identity and ego.
 *
 * Owns the full assembly (image composite, info panel, description, layout,
 * lock overlay) as an invariant. Callers supply only domain contexts as args:
 * the resolved image path/tier, the i18n hook, and the optional feature flags
 * (`isDefenseSkill`, `sanityCost`, `isLocked`). Structure (image, stats) stays
 * visible while name/description suspend for i18n.
 */
export function SkillCard({
  entityId,
  skillId,
  level,
  skillData,
  coinString,
  skillImagePath,
  skillTier,
  useDetailI18n,
  isDefenseSkill = false,
  sanityCost,
  isLocked = false,
}: SkillCardProps) {
  const [showMissing, setShowMissing] = useState(false)

  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0

  const card = (
    <SkillCardLayout
      imageComposite={
        <SkillImageComposite
          skillImagePath={skillImagePath}
          attributeType={attributeType}
          skillTier={skillTier}
          atkType={atkType}
          basePower={basePower}
          coinPower={coinPower}
          onImageError={() => setShowMissing(true)}
          showMissingPlaceholder={showMissing}
        />
      }
      infoPanel={
        <SkillInfoPanelWithSuspense
          entityId={entityId}
          skillId={skillId}
          skillData={skillData}
          coinString={coinString}
          useDetailI18n={useDetailI18n}
          isDefenseSkill={isDefenseSkill}
          sanityCost={sanityCost}
        />
      }
      description={
        <SkillDescriptionWithSuspense
          entityId={entityId}
          skillId={skillId}
          level={level}
          useDetailI18n={useDetailI18n}
        />
      }
    />
  )

  if (!isLocked) return card

  return (
    <div className="relative opacity-50">
      {card}
      <img
        src={getLockIconPath()}
        alt=""
        className="absolute right-8 bottom-8 -z-10 h-30 brightness-20"
      />
    </div>
  )
}
