import { Suspense, useState } from 'react'
import { useParams } from '@tanstack/react-router'

import { EnemyIdSchema } from '@/shared/gameData'
import { BattleDetailSkeleton } from './components/detail/BattleDetailSkeleton'
import { BattleDetailView } from './components/detail/BattleDetailView'
import { ENEMY_DETAIL, useBattleDetailSpec } from './hooks/useBattleDetailData'
import { enemyFilterStore } from './stores/enemyFilterStore'

function EnemyDetailContent() {
  const { id } = useParams({ strict: false })
  const [arrivalCases] = useState(() => enemyFilterStore.store.getState().values.selectedCases)

  if (!id) {
    throw new Error('Enemy ID is required')
  }
  const enemyId = EnemyIdSchema.parse(id)
  const record = useBattleDetailSpec(ENEMY_DETAIL, enemyId)

  return (
    <BattleDetailView
      family="enemy"
      id={enemyId}
      config={ENEMY_DETAIL}
      engagements={record.engagedStages}
      phases={record.phases}
      palette="enemy"
      arrivalCases={arrivalCases}
      fallbackLevel={undefined}
      uptie={undefined}
      badge={record.badge}
    />
  )
}

export default function EnemyDetailPage() {
  return (
    <Suspense fallback={<BattleDetailSkeleton />}>
      <EnemyDetailContent />
    </Suspense>
  )
}
