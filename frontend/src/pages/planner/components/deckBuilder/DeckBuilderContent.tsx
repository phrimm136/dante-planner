import { startTransition, useState, useEffect, useRef } from 'react'
import { MAX_LEVEL, EGO_TYPES } from '@/shared/gameData'
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
} from '../../stores/usePlannerEditorStore'
import { useIdentityListData } from '@/pages/identity'
import { useEGOListData } from '@/pages/ego'
import { useSearchMappingsDeferred } from '@/shared/filter'
import { matchesDeckFilter } from '../../lib/deckFilter'
import type {
  UptieTier,
  ThreadspinTier,
  SinnerEquipment,
  DeckFilterState,
} from '../../types/DeckTypes'
import type { IdentityListItem } from '@/pages/identity'
import type { EGOListItem } from '@/pages/ego'
import { getSinnerCodeFromId } from '@/lib/utils'
import { type SkillData } from './SinnerGrid'
import { DeckLoadoutSection } from './DeckLoadoutSection'
import { DeckCatalogSection } from './DeckCatalogSection'

/** The deck the builder edits, and the writers that own it. */
export interface DeckBuilderDeck {
  equipment: Record<string, SinnerEquipment>
  setEquipment: (
    update: (previous: Record<string, SinnerEquipment>) => Record<string, SinnerEquipment>,
  ) => void
  deploymentOrder: number[]
  setDeploymentOrder: (order: number[]) => void
}

/** Deck-wide commands owned by the surrounding page. */
export interface DeckBuilderActions {
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
  /** Fires when a sinner's identity id changes, so callers can reset its skill EA. */
  onIdentityChange?: (sinnerCode: string) => void
}

export interface DeckBuilderContentProps extends DeckBuilderDeck, DeckBuilderActions {
  filterState: DeckFilterState
  /** False while a closing dialog is still painting its exit animation. */
  isActive: boolean
}

/**
 * Core deck builder UI content.
 * Contains all filtering, sorting, selection, and rendering logic.
 */
export function DeckBuilderContent({
  equipment,
  setEquipment,
  deploymentOrder,
  setDeploymentOrder,
  filterState,
  isActive,
  onImport,
  onExport,
  onResetOrder,
  onIdentityChange,
}: DeckBuilderContentProps) {
  // Scroll position preservation
  const identityScrollRef = useRef<HTMLDivElement>(null)
  const egoScrollRef = useRef<HTMLDivElement>(null)
  const savedScrollPositionRef = useRef<number>(0)

  // Get equipped IDs for selection display
  const equippedIdentityIds = (() => {
    return new Set(Object.values(equipment).map((eq) => eq.identity.id))
  })()

  const equippedEgoIds = (() => {
    const ids = new Set<string>()
    Object.values(equipment).forEach((eq) => {
      Object.values(eq.egos).forEach((ego) => {
        if (ego) ids.add(ego.id)
      })
    })
    return ids
  })()

  const equippedThreadspinMap = (() => {
    const map: Record<string, ThreadspinTier> = {}
    Object.values(equipment).forEach((eq) => {
      Object.values(eq.egos).forEach((ego) => {
        if (ego) map[ego.id] = ego.threadspin
      })
    })
    return map
  })()

  // Sorting snapshot - captured on activation for stable sorting during session
  // Equipped items stay at top even if user unequips them (prevents jarring re-sort)
  const [sortingSnapshot, setSortingSnapshot] = useState<{
    identityIds: Set<string>
    egoIds: Set<string>
    entityMode: string
  } | null>(null)

  const prevActiveRef = useRef(isActive)

  // Capture snapshot when component activates or entity mode changes
  useEffect(() => {
    const justActivated = isActive && !prevActiveRef.current
    prevActiveRef.current = isActive

    if (!isActive) {
      // Dialog closed - clear snapshot for fresh state on next open
      if (sortingSnapshot !== null) {
        setSortingSnapshot(null)
      }
      return
    }

    // Take snapshot if: first time, just reopened, or entity mode changed
    const needsSnapshot =
      sortingSnapshot === null ||
      justActivated ||
      sortingSnapshot.entityMode !== filterState.entityMode

    if (needsSnapshot) {
      setSortingSnapshot({
        identityIds: new Set(equippedIdentityIds),
        egoIds: new Set(equippedEgoIds),
        entityMode: filterState.entityMode,
      })
    }
  }, [isActive, filterState.entityMode, equippedIdentityIds, equippedEgoIds, sortingSnapshot])

  // Extract snapshot sets for sorting (fall back to current equipped if no snapshot)
  const sortingIdentityIds = sortingSnapshot?.identityIds ?? equippedIdentityIds
  const sortingEgoIds = sortingSnapshot?.egoIds ?? equippedEgoIds

  // Restore scroll position after equipment changes
  // Effect runs after render, so DOM is ready - no rAF needed
  useEffect(() => {
    if (savedScrollPositionRef.current === 0) return

    const container =
      filterState.entityMode === 'identity' ? identityScrollRef.current : egoScrollRef.current

    if (container) {
      container.scrollTop = savedScrollPositionRef.current
      savedScrollPositionRef.current = 0
    }
  }, [equippedIdentityIds, equippedEgoIds, filterState.entityMode])

  // Load identity and EGO data (shared cache)
  const { spec: identitySpec, i18n: identityI18n } = useIdentityListData()
  const { spec: egoSpec, i18n: egoI18n } = useEGOListData()

  // Merge spec and i18n into identity/EGO arrays
  const identities: IdentityListItem[] = (() => {
    return Object.entries(identitySpec).map(([id, specData]) => ({
      id,
      name: identityI18n[id] || id,
      rank: specData.rank,
      updateDate: specData.updateDate,
      unitKeywordList: specData.unitKeywordList,
      skillKeywordList: specData.skillKeywordList,
      battleKeywordList: specData.battleKeywordList ?? [],
      attributeTypes: specData.attributeType,
      atkTypes: specData.atkType,
      defenseTypes: specData.defenseType,
      season: specData.season,
    }))
  })()

  const egos: EGOListItem[] = (() => {
    return Object.entries(egoSpec).map(([id, specData]) => ({
      id,
      name: egoI18n[id] || id,
      egoType: specData.egoType,
      skillKeywordList: specData.skillKeywordList,
      battleKeywordList: specData.battleKeywordList ?? [],
      attributeTypes: specData.attributeType,
      atkTypes: specData.atkType,
      updateDate: specData.updateDate,
      season: specData.season,
      maxThreadspin: specData.maxThreadspin,
    }))
  })()

  // Get skill data for the compact identity row
  const skillDataMap: Record<string, SkillData> = (() => {
    const map: Record<string, SkillData> = {}
    Object.values(equipment).forEach((eq) => {
      const spec = identitySpec[eq.identity.id]
      if (spec) {
        map[eq.identity.id] = {
          affinities: spec.attributeType?.slice(0, 3) ?? [],
          atkTypes: spec.atkType?.slice(0, 3) ?? [],
        }
      }
    })
    return map
  })()

  // Get EGO affinity data
  const egoAffinityMap: Record<string, string> = (() => {
    const map: Record<string, string> = {}
    Object.entries(egoSpec).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  })()

  // Sort identities ONCE (stable order - sorting doesn't change on filter)
  // Uses snapshot of equipped IDs to keep equipped items at top
  const searchMappings = useSearchMappingsDeferred()

  const sortedIdentities = (() => {
    return [...identities].sort((a, b) => {
      // Primary: equipped first (using snapshot)
      const aEquipped = sortingIdentityIds.has(a.id) ? 0 : 1
      const bEquipped = sortingIdentityIds.has(b.id) ? 0 : 1
      if (aEquipped !== bEquipped) return aEquipped - bEquipped
      // Secondary: updateDate descending (newer first)
      if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
      // Tertiary: rank descending (higher rarity first)
      if (a.rank !== b.rank) return b.rank - a.rank
      // Quaternary: id descending
      return parseInt(b.id, 10) - parseInt(a.id, 10)
    })
  })()

  const sortedEgos = (() => {
    return [...egos].sort((a, b) => {
      // Primary: equipped first (using snapshot)
      const aEquipped = sortingEgoIds.has(a.id) ? 0 : 1
      const bEquipped = sortingEgoIds.has(b.id) ? 0 : 1
      if (aEquipped !== bEquipped) return aEquipped - bEquipped
      // Secondary: updateDate descending (newer first)
      if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
      // Tertiary: egoType tier descending (ALEPH > WAW > HE > TETH > ZAYIN)
      const tierA = EGO_TYPES.indexOf(a.egoType)
      const tierB = EGO_TYPES.indexOf(b.egoType)
      if (tierA !== tierB) return tierB - tierA
      // Quaternary: sinner descending (sinner 12 > sinner 01)
      const sinnerA = parseInt(a.id.substring(1, 3), 10)
      const sinnerB = parseInt(b.id.substring(1, 3), 10)
      if (sinnerA !== sinnerB) return sinnerB - sinnerA
      // Quinary: id descending
      return parseInt(b.id, 10) - parseInt(a.id, 10)
    })
  })()

  // Compute visible IDs based on filters (fast O(n), no React reconciliation)
  const visibleIdentityIds = (() => {
    const ids = new Set<string>()
    for (const identity of sortedIdentities) {
      if (!matchesDeckFilter(identity, filterState, 'identity', searchMappings)) continue
      ids.add(identity.id)
    }
    return ids
  })()

  const visibleEgoIds = (() => {
    const ids = new Set<string>()
    for (const ego of sortedEgos) {
      if (!matchesDeckFilter(ego, filterState, 'ego', searchMappings)) continue
      ids.add(ego.id)
    }
    return ids
  })()

  // Create EGO lookup map
  const egoMap = (() => {
    const map: Record<string, EGOListItem> = {}
    egos.forEach((e) => {
      map[e.id] = e
    })
    return map
  })()

  // Handlers
  const handleToggleDeploy = (sinnerIndex: number) => {
    startTransition(() => {
      const currentIndex = deploymentOrder.indexOf(sinnerIndex)
      if (currentIndex >= 0) {
        const newOrder = [...deploymentOrder]
        newOrder.splice(currentIndex, 1)
        setDeploymentOrder(newOrder)
      } else {
        setDeploymentOrder([...deploymentOrder, sinnerIndex])
      }
    })
  }

  const handleEquipIdentity = (identityId: string, data: { uptie?: UptieTier; level?: number }) => {
    // Save scroll position before state update
    if (identityScrollRef.current) {
      savedScrollPositionRef.current = identityScrollRef.current.scrollTop
    }

    const sinnerCode = getSinnerCodeFromId(identityId)
    const currentIdentityId = equipment[sinnerCode]?.identity?.id

    startTransition(() => {
      setEquipment((prevEquipment: Record<string, SinnerEquipment>) => {
        const sinnerEquipment = prevEquipment[sinnerCode]
        if (!sinnerEquipment) return prevEquipment
        return {
          ...prevEquipment,
          [sinnerCode]: {
            ...sinnerEquipment,
            identity: {
              id: identityId,
              uptie: data.uptie || 4,
              level: data.level || MAX_LEVEL,
            },
          },
        }
      })

      if (currentIdentityId !== identityId) {
        onIdentityChange?.(sinnerCode)
      }
    })
  }

  const handleEquipEgo = (egoId: string, data: { threadspin?: ThreadspinTier }) => {
    // Save scroll position before state update
    if (egoScrollRef.current) {
      savedScrollPositionRef.current = egoScrollRef.current.scrollTop
    }

    const sinnerCode = getSinnerCodeFromId(egoId)
    const ego = egoMap[egoId]
    startTransition(() => {
      if (!ego) return
      const rank = ego.egoType
      setEquipment((prevEquipment: Record<string, SinnerEquipment>) => {
        const sinnerEquipment = prevEquipment[sinnerCode]
        if (!sinnerEquipment) return prevEquipment
        return {
          ...prevEquipment,
          [sinnerCode]: {
            ...sinnerEquipment,
            egos: {
              ...sinnerEquipment.egos,
              [rank]: {
                id: egoId,
                threadspin: data.threadspin ?? ego.maxThreadspin,
              },
            },
          },
        }
      })
    })
  }

  const handleUnequipEgo = (egoId: string) => {
    // Save scroll position before state update
    if (egoScrollRef.current) {
      savedScrollPositionRef.current = egoScrollRef.current.scrollTop
    }

    const sinnerCode = getSinnerCodeFromId(egoId)
    const ego = egoMap[egoId]
    startTransition(() => {
      if (!ego) return
      const rank = ego.egoType
      // When unequipping ZAYIN, revert to default ZAYIN ego
      if (rank === 'ZAYIN') {
        const sinnerIdPart = sinnerCode.padStart(2, '0')
        const defaultEgoId = `2${sinnerIdPart}01`
        const defaultMaxThreadspin = egoMap[defaultEgoId]?.maxThreadspin ?? 4
        setEquipment((prevEquipment: Record<string, SinnerEquipment>) => {
          const sinnerEquipment = prevEquipment[sinnerCode]
          if (!sinnerEquipment) return prevEquipment
          return {
            ...prevEquipment,
            [sinnerCode]: {
              ...sinnerEquipment,
              egos: {
                ...sinnerEquipment.egos,
                ZAYIN: { id: defaultEgoId, threadspin: defaultMaxThreadspin },
              },
            },
          }
        })
        return
      }
      setEquipment((prevEquipment: Record<string, SinnerEquipment>) => {
        const sinnerEquipment = prevEquipment[sinnerCode]
        if (!sinnerEquipment) return prevEquipment
        const newEgos = { ...sinnerEquipment.egos }
        delete newEgos[rank]
        return {
          ...prevEquipment,
          [sinnerCode]: {
            ...sinnerEquipment,
            egos: newEgos,
          },
        }
      })
    })
  }

  return (
    <div className="space-y-6">
      <DeckLoadoutSection
        entityMode={filterState.entityMode}
        equipment={equipment}
        deploymentOrder={deploymentOrder}
        skillDataMap={skillDataMap}
        egoAffinityMap={egoAffinityMap}
        onToggleDeploy={handleToggleDeploy}
        onImport={onImport}
        onExport={onExport}
        onResetOrder={onResetOrder}
      />

      <DeckCatalogSection
        isActive={isActive}
        entityMode={filterState.entityMode}
        sortedIdentities={sortedIdentities}
        visibleIdentityIds={visibleIdentityIds}
        equippedIdentityIds={equippedIdentityIds}
        identityScrollRef={identityScrollRef}
        onEquipIdentity={handleEquipIdentity}
        sortedEgos={sortedEgos}
        visibleEgoIds={visibleEgoIds}
        equippedEgoIds={equippedEgoIds}
        equippedThreadspinMap={equippedThreadspinMap}
        egoScrollRef={egoScrollRef}
        onEquipEgo={handleEquipEgo}
        onUnequipEgo={handleUnequipEgo}
      />
    </div>
  )
}

/** Props a store-bound caller supplies; the deck and filter come from the store. */
export type StoreBoundDeckBuilderContentProps = DeckBuilderActions & { isActive: boolean }

/** Renders the builder against the deck and filter held by the planner editor store. */
export function StoreBoundDeckBuilderContent(props: StoreBoundDeckBuilderContentProps) {
  const equipment = usePlannerEditorStore((s) => s.equipment)
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const filterState = usePlannerEditorStore((s) => s.deckFilterState)

  return (
    <DeckBuilderContent
      {...props}
      equipment={equipment}
      setEquipment={setEquipment}
      deploymentOrder={deploymentOrder}
      setDeploymentOrder={setDeploymentOrder}
      filterState={filterState}
    />
  )
}

/** Props the tracker supplies; its filter state is session-only. */
export type TrackerDeckBuilderContentProps = Omit<DeckBuilderContentProps, 'filterState'>

/**
 * Renders the builder against a caller-owned session deck.
 *
 * The catalog subtree — filter bar and both grids — reads its own UI state from
 * the planner editor store, so the tracker gives it a private one. Only the
 * filter and the progressive render counter are read from that store; the deck
 * stays with the caller. It lives and dies with this mount, so each visit to
 * the pane starts from the default filters.
 */
export function TrackerDeckBuilderContent(props: TrackerDeckBuilderContentProps) {
  return (
    <PlannerEditorStoreProvider>
      <StoreFilteredDeckBuilderContent {...props} />
    </PlannerEditorStoreProvider>
  )
}

function StoreFilteredDeckBuilderContent(props: TrackerDeckBuilderContentProps) {
  const filterState = usePlannerEditorStore((s) => s.deckFilterState)

  return <DeckBuilderContent {...props} filterState={filterState} />
}
