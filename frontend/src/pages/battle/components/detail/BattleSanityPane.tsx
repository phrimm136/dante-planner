import { Suspense } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import { FormattedDescription } from '@/shared/gameText'
import { PanicName, PanicTypeSection, SanityConditionsSectionI18n } from '@/shared/sanity'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import { phaseTexts, sanityConditionIds } from '../../lib/battleDetail'
import type { BattlePhase } from '../../schemas/BattleDetailSchemas'
import type { BattleTextSource } from './BattleSkillCard'

function BattlePanicSection({
  source,
  panicType,
}: {
  source: BattleTextSource
  panicType: string
}) {
  const i18n = useBattleDetailI18n(source.config, source.id)
  const sanity = phaseTexts(i18n, source.unitId, source.phaseId)?.sanity
  if (sanity?.panicText === undefined) return null

  return (
    <PanicTypeSection
      panicType={panicType}
      nameSlot={<PanicName name={sanity.panicName ?? ''} />}
      descSlot={<FormattedDescription text={sanity.panicText} />}
      flavor={sanity.panicFlavor}
      {...(sanity.lowMoraleText !== undefined && {
        lowMoraleSlot: <FormattedDescription text={sanity.lowMoraleText} />,
      })}
    />
  )
}

export function BattleSanityPane({
  source,
  sanity,
}: {
  source: BattleTextSource
  sanity: BattlePhase['sanity']
}) {
  const conditions = sanity.mentalConditionInfo

  return (
    <div data-slot="sanity-pane" className="border rounded">
      {sanity.panicType !== undefined && sanity.panicType !== null && (
        <Suspense fallback={<Skeleton className="h-28 w-full" />}>
          <BattlePanicSection source={source} panicType={String(sanity.panicType)} />
        </Suspense>
      )}
      <SanityConditionsSectionI18n
        addConditions={sanityConditionIds(conditions?.add ?? [])}
        minConditions={sanityConditionIds(conditions?.min ?? [])}
      />
    </div>
  )
}
