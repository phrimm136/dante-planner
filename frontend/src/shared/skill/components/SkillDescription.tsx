import { getCoinDescIconPath } from '@/shared/assets'
import { type SkillDescEntry } from '@/shared/gameData'
import { FormattedDescription } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { FLAVOR_TEXT_COLOR } from '@/lib/constants'

interface SkillDescriptionProps {
  descData: SkillDescEntry
  flavor?: string
}

export function SkillDescription({ descData, flavor }: SkillDescriptionProps) {
  const { desc, coinDescs } = descData

  return (
    <div className="text-sm space-y-2">
      <div className="pb-1">
        <FormattedDescription text={desc ?? ''} />
      </div>

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

export function SkillDescriptionSkeleton() {
  return (
    <div className="text-sm space-y-2">
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
    </div>
  )
}

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
