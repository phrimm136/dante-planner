import { Suspense } from 'react'

import { useIdentityDetailI18n } from '@/hooks/useIdentityDetailData'
import { getCoinDescIconPath } from '@/lib/assetPaths'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { Skeleton } from '@/components/ui/skeleton'
import type { IdentitySkillDescEntry, Uptie } from '@/types/IdentityTypes'

interface SkillDescriptionProps {
  descData: IdentitySkillDescEntry
}

interface SkillDescriptionWithSuspenseProps {
  identityId: string
  skillId: number
  uptie: Uptie
}

/**
 * SkillDescription - Displays skill description and coin descriptions
 *
 * Layout:
 * 1. Skill description (desc)
 * 2. Coin descriptions (coinDescs) with numbered coin icons, tabbed
 */
export function SkillDescription({ descData }: SkillDescriptionProps) {
  const { desc, coinDescs } = descData

  return (
    <div className="text-sm space-y-2">
      {/* Main skill description */}
      <div className="pb-1">
        <FormattedDescription text={desc} />
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
                  className="w-8 h-8 shrink-0 mt-0.5"
                />
                <div className="mt-4">
                  <FormattedDescription text={coinDesc} />
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// =============================================================================
// Granular I18n Version
// =============================================================================

/**
 * SkillDescription with granular i18n Suspense.
 * Shows skeleton, then fetches and renders description.
 */
export function SkillDescriptionWithSuspense({
  identityId,
  skillId,
  uptie,
}: SkillDescriptionWithSuspenseProps) {
  return (
    <Suspense fallback={<SkillDescriptionSkeleton />}>
      <SkillDescriptionContent identityId={identityId} skillId={skillId} uptie={uptie} />
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
 * Get merged skill description for a specific uptie level.
 */
function getMergedSkillDesc(
  descs: IdentitySkillDescEntry[],
  uptie: Uptie
): IdentitySkillDescEntry {
  const merged: IdentitySkillDescEntry = {}
  for (let i = 0; i < uptie; i++) {
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
 * Internal: Fetches and renders skill description.
 */
function SkillDescriptionContent({
  identityId,
  skillId,
  uptie,
}: SkillDescriptionWithSuspenseProps) {
  const i18n = useIdentityDetailI18n(identityId)
  const skillI18n = i18n.skills[String(skillId)]
  const descData = getMergedSkillDesc(skillI18n?.descs ?? [], uptie)

  return <SkillDescription descData={descData} />
}
