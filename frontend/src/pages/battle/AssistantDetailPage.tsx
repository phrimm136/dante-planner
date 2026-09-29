import { Suspense } from 'react'
import { useParams } from '@tanstack/react-router'

import { AssistantIdSchema } from '@/shared/gameData'
import { BattleDetailSkeleton } from './components/detail/BattleDetailSkeleton'
import { BattleDetailView } from './components/detail/BattleDetailView'
import { ASSISTANT_DETAIL, useBattleDetailSpec } from './hooks/useBattleDetailData'

const NO_ARRIVAL_CASES: ReadonlySet<never> = new Set()

function AssistantDetailContent() {
  const { id } = useParams({ strict: false })

  if (!id) {
    throw new Error('Assistant ID is required')
  }
  const assistantId = AssistantIdSchema.parse(id)
  const record = useBattleDetailSpec(ASSISTANT_DETAIL, assistantId)

  return (
    <BattleDetailView
      family="assistant"
      id={assistantId}
      config={ASSISTANT_DETAIL}
      engagements={record.engagedStages}
      phases={record.phases}
      palette="player"
      arrivalCases={NO_ARRIVAL_CASES}
      fallbackLevel={record.level}
      uptie={undefined}
    />
  )
}

export default function AssistantDetailPage() {
  return (
    <Suspense fallback={<BattleDetailSkeleton />}>
      <AssistantDetailContent />
    </Suspense>
  )
}
