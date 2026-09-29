import { Suspense } from 'react'

import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { BattleModeRow } from './components/BattleModeRow'
import { BattlePanel } from './components/BattlePanel'
import { BATTLE_GEOMETRY } from './lib/cardLayout'

export default function BattlePage() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className={SECTION_STYLES.LAYOUT.column}>
        <BattleModeRow />
        <Suspense fallback={<ListPageSkeleton geometry={BATTLE_GEOMETRY} />}>
          <BattlePanel />
        </Suspense>
      </div>
    </div>
  )
}
