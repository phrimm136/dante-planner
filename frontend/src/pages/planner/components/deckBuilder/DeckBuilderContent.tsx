import { startTransition, useState, useEffect, useRef } from 'react'
import { MAX_LEVEL, EGO_TYPES, EGOIdSchema } from '@/shared/gameData'
import type { EGOGiftId, EGOId, IdentityId } from '@/shared/gameData'
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
} from '../../stores/usePlannerEditorStore'
import { useIdentityListSpec, useIdentityListI18n, toIdentityEntity } from '@/pages/identity'
import { useEGOListSpec, useEGOListI18n, toEGOEntity } from '@/pages/ego'
import { useSearchMappings } from '@/shared/filter'
import { matchesDeckFilter } from '../../lib/deckFilter'
import { collectOwnedGiftIds } from '../../lib/deckEA'
import type {
  UptieTier,
  ThreadspinTier,
  SinnerEquipment,
  DeckFilterState,
} from '../../types/DeckTypes'
import type { IdentityEntity } from '@/pages/identity'
import type { EGOEntity } from '@/pages/ego'
import { getSinnerCodeFromId } from '@/lib/utils'
import { type SkillData } from './SinnerGrid'
import { DeckLoadoutSection } from './DeckLoadoutSection'
import { DeckCatalogSection } from './DeckCatalogSection'

export interface DeckBuilderDeck {
  equipment: Record<string, SinnerEquipment>
  setEquipment: (
    update: (previous: Record<string, SinnerEquipment>) => Record<string, SinnerEquipment>,
  ) => void
  deploymentOrder: number[]
  setDeploymentOrder: (order: number[]) => void
}

export interface DeckBuilderActions {
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
  onIdentityChange?: (sinnerCode: string) => void
}

export interface DeckBuilderContentProps extends DeckBuilderDeck, DeckBuilderActions {
  filterState: DeckFilterState
  isActive: boolean
  ownedGiftIds: ReadonlySet<EGOGiftId>
}

export function DeckBuilderContent({
  equipment,
  setEquipment,
  deploymentOrder,
  setDeploymentOrder,
  filterState,
  isActive,
  ownedGiftIds,
  onImport,
  onExport,
  onResetOrder,
  onIdentityChange,
}: DeckBuilderContentProps) {
  const identityScrollRef = useRef<HTMLDivElement>(null)
  const egoScrollRef = useRef<HTMLDivElement>(null)
  const savedScrollPositionRef = useRef<number>(0)

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

  const [sortingSnapshot, setSortingSnapshot] = useState<{
    identityIds: Set<string>
    egoIds: Set<string>
    entityMode: string
  } | null>(null)

  const prevActiveRef = useRef(isActive)

  useEffect(() => {
    const justActivated = isActive && !prevActiveRef.current
    prevActiveRef.current = isActive

    if (!isActive) {
      if (sortingSnapshot !== null) {
        setSortingSnapshot(null)
      }
      return
    }

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

  const sortingIdentityIds = sortingSnapshot?.identityIds ?? equippedIdentityIds
  const sortingEgoIds = sortingSnapshot?.egoIds ?? equippedEgoIds

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

  const identitySpec = useIdentityListSpec()
  const identityI18n = useIdentityListI18n()
  const egoSpec = useEGOListSpec()
  const egoI18n = useEGOListI18n()

  const identities: IdentityEntity[] = Object.entries(identitySpec).map(([id, entry]) =>
    toIdentityEntity(id, entry, identityI18n[id] || id),
  )

  const egos: EGOEntity[] = Object.entries(egoSpec).map(([id, entry]) =>
    toEGOEntity(id, entry, egoI18n[id] || id),
  )

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

  const egoAffinityMap: Record<string, string> = (() => {
    const map: Record<string, string> = {}
    Object.entries(egoSpec).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  })()

  const searchMappings = useSearchMappings()

  const sortedIdentities = (() => {
    return [...identities].sort((a, b) => {
      const aEquipped = sortingIdentityIds.has(a.id) ? 0 : 1
      const bEquipped = sortingIdentityIds.has(b.id) ? 0 : 1
      if (aEquipped !== bEquipped) return aEquipped - bEquipped
      if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
      if (a.rank !== b.rank) return b.rank - a.rank
      return parseInt(b.id, 10) - parseInt(a.id, 10)
    })
  })()

  const sortedEgos = (() => {
    return [...egos].sort((a, b) => {
      const aEquipped = sortingEgoIds.has(a.id) ? 0 : 1
      const bEquipped = sortingEgoIds.has(b.id) ? 0 : 1
      if (aEquipped !== bEquipped) return aEquipped - bEquipped
      if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
      const tierA = EGO_TYPES.indexOf(a.egoType)
      const tierB = EGO_TYPES.indexOf(b.egoType)
      if (tierA !== tierB) return tierB - tierA
      const sinnerA = parseInt(a.id.substring(1, 3), 10)
      const sinnerB = parseInt(b.id.substring(1, 3), 10)
      if (sinnerA !== sinnerB) return sinnerB - sinnerA
      return parseInt(b.id, 10) - parseInt(a.id, 10)
    })
  })()

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

  const egoMap = (() => {
    const map: Record<string, EGOEntity> = {}
    egos.forEach((e) => {
      map[e.id] = e
    })
    return map
  })()

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

  const handleEquipIdentity = (
    identityId: IdentityId,
    data: { uptie?: UptieTier; level?: number },
  ) => {
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

  const handleEquipEgo = (egoId: EGOId, data: { threadspin?: ThreadspinTier }) => {
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
    if (egoScrollRef.current) {
      savedScrollPositionRef.current = egoScrollRef.current.scrollTop
    }

    const sinnerCode = getSinnerCodeFromId(egoId)
    const ego = egoMap[egoId]
    startTransition(() => {
      if (!ego) return
      const rank = ego.egoType
      if (rank === 'ZAYIN') {
        const sinnerIdPart = sinnerCode.padStart(2, '0')
        const defaultEgoId = EGOIdSchema.parse(`2${sinnerIdPart}01`)
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
        ownedGiftIds={ownedGiftIds}
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

export type StoreBoundDeckBuilderContentProps = DeckBuilderActions & { isActive: boolean }

export function StoreBoundDeckBuilderContent(props: StoreBoundDeckBuilderContentProps) {
  const equipment = usePlannerEditorStore((s) => s.equipment)
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const filterState = usePlannerEditorStore((s) => s.deckFilterState)
  const selectedGiftIds = usePlannerEditorStore((s) => s.selectedGiftIds)
  const observationGiftIds = usePlannerEditorStore((s) => s.observationGiftIds)
  const comprehensiveGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const floorSelections = usePlannerEditorStore((s) => s.floorSelections)
  const ownedGiftIds = collectOwnedGiftIds({
    selectedGiftIds,
    observationGiftIds,
    comprehensiveGiftIds,
    floorSelections,
  })

  return (
    <DeckBuilderContent
      {...props}
      equipment={equipment}
      setEquipment={setEquipment}
      deploymentOrder={deploymentOrder}
      setDeploymentOrder={setDeploymentOrder}
      filterState={filterState}
      ownedGiftIds={ownedGiftIds}
    />
  )
}

export type TrackerDeckBuilderContentProps = Omit<DeckBuilderContentProps, 'filterState'>

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
