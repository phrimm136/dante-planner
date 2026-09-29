import { Suspense, useState } from 'react'
import { useParams } from '@tanstack/react-router'

import { SinnerEventIdSchema } from '@/shared/gameData'
import { BattleDetailSkeleton } from './components/detail/BattleDetailSkeleton'
import { BattleDetailView } from './components/detail/BattleDetailView'
import { SINNER_EVENT_DETAIL, useBattleDetailSpec } from './hooks/useBattleDetailData'
import { battleModeStore } from './stores/battleModeStore'

function SinnerEventDetailContent() {
  const { id } = useParams({ strict: false })
  const [arrivalMode] = useState(() => battleModeStore.getState().mode)

  if (!id) {
    throw new Error('Event sinner ID is required')
  }
  const sinnerEventId = SinnerEventIdSchema.parse(id)
  const group = useBattleDetailSpec(SINNER_EVENT_DETAIL, sinnerEventId)

  return (
    <BattleDetailView
      family="sinnerEvent"
      id={sinnerEventId}
      config={SINNER_EVENT_DETAIL}
      group={group}
      palette="player"
      arrivalMode={arrivalMode}
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
