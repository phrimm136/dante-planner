import { useUnitKeywords, useUnitKeywordsAll } from '@/shared/filter'
import type { UnitKeywords } from '@/shared/filter'
import { applyTextStyles } from '@/shared/gameText'

const HIDDEN_TRAITS = new Set(['BASE_APPEARANCE', 'SMALL'])

interface TraitsI18nProps {
  traits: string[]
}

export function TraitsI18n({ traits }: TraitsI18nProps) {
  return <TraitChips traits={traits} names={useUnitKeywords()} />
}

export function AllTraitsI18n({ traits }: TraitsI18nProps) {
  return <TraitChips traits={traits} names={useUnitKeywordsAll()} />
}

function TraitChips({ traits, names }: { traits: string[]; names: UnitKeywords }) {
  const visibleTraits = [...new Set(traits)].filter((trait) => !HIDDEN_TRAITS.has(trait))

  const translatedTraits = visibleTraits.flatMap((trait) => {
    const translated = names[trait]
    return translated === undefined ? [] : [{ key: trait, text: translated }]
  })

  if (translatedTraits.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap gap-1">
      {translatedTraits.map((trait) => (
        <span
          key={trait.key}
          className="px-2 py-0.5 text-xs bg-secondary text-secondary-foreground border border-border"
        >
          {applyTextStyles(trait.text)}
        </span>
      ))}
    </div>
  )
}
