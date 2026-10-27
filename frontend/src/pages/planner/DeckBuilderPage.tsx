import { Suspense, useId } from 'react'

import { PlannerStoreProvider } from './stores/usePlannerStore'

import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'

import { DeckBuilderBlock } from './components/deckBuilder/DeckBuilderBlock'
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { PLANNER_CONFIG, SECTION_STYLES } from '@/lib/constants'

function DeckBuilderPageContent() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <DeckBuilderBlock mdVersion={PLANNER_CONFIG.mdCurrentVersion} />
    </div>
  )
}

function DeckBuilderPageStore() {
  const storeKey = useId()
  const identityMaxLevel = useIdentityMaxLevel(PLANNER_CONFIG.mdCurrentVersion)

  return (
    <PlannerStoreProvider key={storeKey} identityMaxLevel={identityMaxLevel}>
      <DeckBuilderPageContent />
    </PlannerStoreProvider>
  )
}

export default function DeckBuilderPage() {
  return (
    <Suspense fallback={<DeckBuilderPageSkeleton />}>
      <DeckBuilderPageStore />
    </Suspense>
  )
}
