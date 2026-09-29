import { PanicTypeSectionI18n, SanityConditionsSectionI18n } from '@/shared/sanity'
import { sanityConditionIds } from '../../lib/battleDetail'
import type { BattlePhase } from '../../schemas/BattleDetailSchemas'

export function BattleSanityPane({ sanity }: { sanity: BattlePhase['sanity'] }) {
  const conditions = sanity.mentalConditionInfo

  return (
    <div className="border rounded p-4 space-y-4">
      {sanity.panicType !== undefined && sanity.panicType !== null && (
        <PanicTypeSectionI18n panicType={String(sanity.panicType)} />
      )}
      <SanityConditionsSectionI18n
        addConditions={sanityConditionIds(conditions?.add ?? [])}
        minConditions={sanityConditionIds(conditions?.min ?? [])}
      />
    </div>
  )
}
