import { Suspense, useState } from 'react'
import { useParams } from '@tanstack/react-router'

import { AssistantIdSchema } from '@/shared/gameData'
import { BattleDetailSkeleton } from './components/detail/BattleDetailSkeleton'
import { BattleDetailView } from './components/detail/BattleDetailView'
import { ASSISTANT_DETAIL, useBattleDetailSpec } from './hooks/useBattleDetailData'
import { battleModeStore } from './stores/battleModeStore'

function AssistantDetailContent() {
  const { id } = useParams({ strict: false })
  const [arrivalMode] = useState(() => battleModeStore.getState().mode)

  if (!id) {
    throw new Error('Assistant ID is required')
  }
  const assistantId = AssistantIdSchema.parse(id)
  const group = useBattleDetailSpec(ASSISTANT_DETAIL, assistantId)

  return (
    <BattleDetailView
      family="assistant"
      id={assistantId}
      config={ASSISTANT_DETAIL}
      group={group}
      palette="player"
      arrivalMode={arrivalMode}
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
