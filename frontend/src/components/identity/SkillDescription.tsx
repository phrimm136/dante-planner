import { getCoinDescIconPath } from '@/lib/assetPaths'
import type { SkillI18nData } from '@/types/IdentityTypes'

interface SkillDescriptionProps {
  skillI18n: SkillI18nData
}

/**
 * SkillDescription - Displays skill description and coin descriptions
 *
 * Layout:
 * 1. Skill description (Desc)
 * 2. Coin descriptions (CoinDescs) with numbered coin icons, tabbed
 */
export function SkillDescription({ skillI18n }: SkillDescriptionProps) {
  const { desc, coinDescs } = skillI18n

  return (
    <div className="text-sm space-y-2">
      {/* Main skill description */}
      {desc && <div className="text-muted-foreground">{desc}</div>}

      {/* Coin descriptions */}
      {coinDescs && coinDescs.length > 0 && (
        <div className="space-y-1">
          {coinDescs.map((coinDesc, index) => {
            if (!coinDesc) return null

            const coinIconPath = getCoinDescIconPath(index)

            return (
              <div key={index} className="flex gap-2 pl-4">
                <img
                  src={coinIconPath}
                  alt={`Coin ${index + 1}`}
                  className="w-4 h-4 flex-shrink-0 mt-0.5"
                />
                <div className="text-muted-foreground">{coinDesc}</div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
