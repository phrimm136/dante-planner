import { getCoinDescIconPath } from '@/lib/assetPaths'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import type { IdentitySkillDescEntry } from '@/types/IdentityTypes'

interface SkillDescriptionProps {
  descData: IdentitySkillDescEntry
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
              <div key={index} className="flex gap-2 pl-4">
                <img
                  src={coinIconPath}
                  alt={`Coin ${index + 1}`}
                  className="w-4 h-4 shrink-0 mt-0.5"
                />
                <FormattedDescription text={coinDesc} />
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
