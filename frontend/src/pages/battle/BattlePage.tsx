import { Suspense } from 'react'
import { useStore } from 'zustand'

import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { AssistantPanel } from './components/AssistantPanel'
import { BattleTypeRow } from './components/BattleTypeRow'
import { EnemyPanel } from './components/EnemyPanel'
import { SinnerEventPanel } from './components/SinnerEventPanel'
import { BATTLE_GEOMETRY } from './lib/cardLayout'
import { battleTypeStore, selectBattleType } from './stores/battleTypeStore'

function ActivePanel() {
  const entityType = useStore(battleTypeStore, selectBattleType)

  switch (entityType) {
    case 'enemy':
      return <EnemyPanel />
    case 'assistant':
      return <AssistantPanel />
    case 'sinnerEvent':
      return <SinnerEventPanel />
  }
}

export default function BattlePage() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className={SECTION_STYLES.LAYOUT.column}>
        <BattleTypeRow />
        <Suspense fallback={<ListPageSkeleton geometry={BATTLE_GEOMETRY} />}>
          <ActivePanel />
        </Suspense>
      </div>
    </div>
  )
}
