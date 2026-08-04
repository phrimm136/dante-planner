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

/**
 * Skeleton stand-in while a slice's description slot suspends for i18n.
 */
export function SkillDescriptionSkeleton() {
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
