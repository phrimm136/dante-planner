import { Suspense, useId } from 'react'

import { DEFAULT_SKILL_EA } from '@/shared/gameData'

import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  useStoreDeck,
  useStoreOwnedGiftIds,
} from './stores/usePlannerEditorStore'

import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'

import { DeckBuilderBlock } from './components/deckBuilder/DeckBuilderBlock'
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { PLANNER_CONFIG, SECTION_STYLES } from '@/lib/constants'

function DeckBuilderPageContent() {
  const deck = useStoreDeck()
  const ownedGiftIds = useStoreOwnedGiftIds()
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <DeckBuilderBlock
        deck={deck}
        ownedGiftIds={ownedGiftIds}
        mdVersion={PLANNER_CONFIG.mdCurrentVersion}
        onIdentityChange={(sinnerCode) => {
          updateSinnerSkillEA(sinnerCode, { ...DEFAULT_SKILL_EA })
        }}
      />
    </div>
  )
}

function DeckBuilderPageStore() {
  const storeKey = useId()
  const identityMaxLevel = useIdentityMaxLevel(PLANNER_CONFIG.mdCurrentVersion)

  return (
    <PlannerEditorStoreProvider key={storeKey} identityMaxLevel={identityMaxLevel}>
      <DeckBuilderPageContent />
    </PlannerEditorStoreProvider>
  )
}

export default function DeckBuilderPage() {
  return (
    <Suspense fallback={<DeckBuilderPageSkeleton />}>
      <DeckBuilderPageStore />
    </Suspense>
  )
}
