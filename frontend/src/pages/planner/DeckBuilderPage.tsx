import { Suspense, useId } from 'react'

import { PlannerStoreProvider } from './stores/usePlannerStore'

import { DeckBuilderBlock } from './components/deckBuilder/DeckBuilderBlock'
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { PLANNER_CONFIG, SECTION_STYLES } from '@/lib/constants'

function DeckBuilderPageContent() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <DeckBuilderBlock />
    </div>
  )
}

function DeckBuilderPageStore() {
  const storeKey = useId()

  return (
    <PlannerStoreProvider key={storeKey} mdVersion={PLANNER_CONFIG.mdCurrentVersion}>
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
