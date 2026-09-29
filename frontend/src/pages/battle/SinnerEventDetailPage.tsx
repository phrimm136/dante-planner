import { Suspense } from 'react'
import { useParams } from '@tanstack/react-router'

import { SinnerEventIdSchema } from '@/shared/gameData'
import { BattleDetailSkeleton } from './components/detail/BattleDetailSkeleton'
import { BattleDetailView } from './components/detail/BattleDetailView'
import { SINNER_EVENT_DETAIL, useBattleDetailSpec } from './hooks/useBattleDetailData'

const NO_ARRIVAL_CASES: ReadonlySet<never> = new Set()

function SinnerEventDetailContent() {
  const { id } = useParams({ strict: false })

  if (!id) {
    throw new Error('Event sinner ID is required')
  }
  const sinnerEventId = SinnerEventIdSchema.parse(id)
  const record = useBattleDetailSpec(SINNER_EVENT_DETAIL, sinnerEventId)

  return (
    <BattleDetailView
      family="sinnerEvent"
      id={sinnerEventId}
      config={SINNER_EVENT_DETAIL}
      engagements={record.engagedStages}
      phases={record.phases}
      palette="player"
      arrivalCases={NO_ARRIVAL_CASES}
      fallbackLevel={record.level}
      uptie={record.uptie}
    />
  )
}

export default function SinnerEventDetailPage() {
  return (
    <Suspense fallback={<BattleDetailSkeleton />}>
      <SinnerEventDetailContent />
    </Suspense>
  )
}
