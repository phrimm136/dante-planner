import { Suspense, useState } from 'react'
import { useParams } from '@tanstack/react-router'

import { EnemyIdSchema } from '@/shared/gameData'
import { BattleDetailSkeleton } from './components/detail/BattleDetailSkeleton'
import { BattleDetailView } from './components/detail/BattleDetailView'
import { ENEMY_DETAIL, useBattleDetailSpec } from './hooks/useBattleDetailData'
import { battleTypeStore } from './stores/battleTypeStore'

function EnemyDetailContent() {
  const { id } = useParams({ strict: false })
  const [arrivalMode] = useState(() => battleTypeStore.getState().mode)

  if (!id) {
    throw new Error('Enemy ID is required')
  }
  const enemyId = EnemyIdSchema.parse(id)
  const group = useBattleDetailSpec(ENEMY_DETAIL, enemyId)

  return (
    <BattleDetailView
      family="enemy"
      id={enemyId}
      config={ENEMY_DETAIL}
      group={group}
      palette="enemy"
      arrivalMode={arrivalMode}
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
