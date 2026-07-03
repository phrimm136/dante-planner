import { Suspense } from 'react'

import { getCoinDescIconPath } from '@/shared/assets'
import { FLAVOR_TEXT_COLOR, type SkillDescEntry } from '@/shared/gameData'
import { FormattedDescription } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'

interface SkillDescriptionProps {
  descData: SkillDescEntry
  /** Per-skill lore line — sibling to descData, since raw flavor is not per-level. */
  flavor?: string
}

/**
 * SkillDescription - Displays skill description and coin descriptions
 *
 * Layout:
 * 1. Skill description (desc)
 * 2. Coin descriptions (coinDescs) with numbered coin icons, tabbed
 * 3. Flavor lore line (mirrors in-game `[Text]SkillInfoFlavor` TMP)
 */
export function SkillDescription({ descData, flavor }: SkillDescriptionProps) {
  const { desc, coinDescs } = descData

  return (
    <div className="text-sm space-y-2">
      {/* Main skill description */}
      <div className="pb-1">
        <FormattedDescription text={desc ?? ''} />
      </div>

      {/* Coin descriptions */}
      {coinDescs && coinDescs.length > 0 && (
        <div className="space-y-1">
          {coinDescs.map((coinDesc: string, index: number) => {
            if (!coinDesc) return null

            const coinIconPath = getCoinDescIconPath(index)

            return (
              <div key={index} className="flex gap-2">
                <img
                  src={coinIconPath}
                  alt={`Coin ${index + 1}`}
                  className="w-9 h-9 shrink-0 mt-0.5"
                />
                <div className="mt-4">
                  <FormattedDescription text={coinDesc} />
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Flavor text — mirrors in-game [Text]SkillInfoFlavor TMP */}
      {flavor && (
        <p
          data-testid="skill-flavor"
          className="italic whitespace-pre-line pt-1"
          style={{ color: FLAVOR_TEXT_COLOR }}
        >
          {flavor}
        </p>
      )}
    </div>
  )
}

// =============================================================================
// Granular I18n Version
// =============================================================================

/** Minimal shape of the per-entity detail-i18n a skill card needs. */
interface SkillDetailI18n {
  skills: Record<string, { descs?: SkillDescEntry[]; flavor?: string } | undefined>
}

interface SkillDescriptionWithSuspenseProps {
  entityId: string
  skillId: number
  /** Enhancement level (identity uptie 1–4, ego threadspin 1–5). */
  level: number
  /**
   * The owning slice's detail-i18n hook, injected so this shared component
   * stays free of any `@/pages/*` import (sink rule). Identity passes
   * `useIdentityDetailI18n`; ego passes `useEGODetailI18n`.
   */
  useDetailI18n: (id: string) => SkillDetailI18n
}

/**
 * SkillDescription with granular i18n Suspense.
 * Shows skeleton, then fetches and renders description.
 */
export function SkillDescriptionWithSuspense(props: SkillDescriptionWithSuspenseProps) {
  return (
    <Suspense fallback={<SkillDescriptionSkeleton />}>
      <SkillDescriptionContent {...props} />
    </Suspense>
  )
}

/**
 * Skeleton for skill description.
 */
function SkillDescriptionSkeleton() {
  return (
    <div className="text-sm space-y-2">
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
    </div>
  )
}

/**
 * Merge skill-description entries up to the given level. Earlier levels provide
 * base values, later levels override.
 */
export function getMergedSkillDesc(descs: SkillDescEntry[], level: number): SkillDescEntry {
  const merged: SkillDescEntry = {}
  for (let i = 0; i < level; i++) {
    const current = descs[i]
    if (!current) continue
    if (current.desc !== undefined && current.desc !== '') {
      merged.desc = current.desc
    }
    if (current.coinDescs && current.coinDescs.length > 0) {
      merged.coinDescs = current.coinDescs
    }
  }
  return merged
}

/**
 * Internal: fetches i18n via the injected hook and renders the description.
 */
function SkillDescriptionContent({
  entityId,
  skillId,
  level,
  useDetailI18n,
}: SkillDescriptionWithSuspenseProps) {
  const i18n = useDetailI18n(entityId)
  const skillI18n = i18n.skills[String(skillId)]
  const descData = getMergedSkillDesc(skillI18n?.descs ?? [], level)

  return <SkillDescription descData={descData} flavor={skillI18n?.flavor} />
}
