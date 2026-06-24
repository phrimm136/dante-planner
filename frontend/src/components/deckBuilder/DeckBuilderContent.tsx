import { useMemo, useCallback, startTransition, useState, useEffect, useRef } from 'react'
import { MAX_LEVEL, DEFAULT_DEPLOYMENT_MAX, SECTION_STYLES, EGO_TYPES } from '@/lib/constants'
import { createDefaultDeckFilterState } from '@/stores/usePlannerEditorStore'
import { useIdentityListData } from '@/pages/identity'
import { useEGOListData } from '@/pages/ego'
import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'
import { usePlannerEditorStoreSafe, usePlannerEditorStoreApiSafe } from '@/stores/usePlannerEditorStore'
import { matchesDeckFilter } from '@/lib/deckFilter'
import type { UptieTier, ThreadspinTier, DeckState, SinnerEquipment, DeckFilterState } from '@/types/DeckTypes'
import type { IdentityListItem } from '@/pages/identity'
import type { EGOListItem } from '@/pages/ego'
import { getSinnerCodeFromId } from '@/lib/utils'
import { type SkillData } from './SinnerGrid'
import { CompactIdentityRow } from './CompactIdentityRow'
import { CompactEgoGrid } from './CompactEgoGrid'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'
import { DeckFilterBar } from './DeckFilterBar'
import { IdentityGrid } from './IdentityGrid'
import { EgoGrid } from './EgoGrid'

/** Base props shared by both modes */
interface DeckBuilderContentBaseProps {
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
  /** Override equipment from store (for tracker mode) */
  equipmentOverride?: Record<string, SinnerEquipment>
  /** Override deploymentOrder from store (for tracker mode) */
  deploymentOrderOverride?: number[]
  /** Override setEquipment from store (for tracker mode) */
  setEquipmentOverride?: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>
  /** Override setDeploymentOrder from store (for tracker mode) */
  setDeploymentOrderOverride?: React.Dispatch<React.SetStateAction<number[]>>
  /** Callback when identity changes (different ID, not uptie/level). Resets skill EA in edit and tracker modes. */
  onIdentityChange?: (sinnerCode: string) => void
}

/** Standalone page mode - no dialog tracking needed */
interface StandaloneModeProps extends DeckBuilderContentBaseProps {
  mode?: 'standalone'
  open?: never
}

/** Dialog mode - requires open state for snapshot timing */
interface DialogModeProps extends DeckBuilderContentBaseProps {
  mode: 'dialog'
  /** Signal that dialog is open - used for snapshot timing */
  open: boolean
}

type DeckBuilderContentProps = StandaloneModeProps | DialogModeProps

const BATCH_SIZE = 10

/**
 * Core deck builder UI content.
 * Contains all filtering, sorting, selection, and rendering logic.
 * Used by both DeckBuilderPane (dialog) and DeckBuilderPage (standalone).
 */
export function DeckBuilderContent(props: DeckBuilderContentProps) {
  const { onImport, onExport, onResetOrder, equipmentOverride, deploymentOrderOverride, setEquipmentOverride, setDeploymentOrderOverride, onIdentityChange } = props
  const isDialogMode = props.mode === 'dialog'
  const open = isDialogMode ? props.open : true

  // Store state (safe - returns undefined if outside context)
  const storeEquipment = usePlannerEditorStoreSafe((s) => s.equipment)
  const storeSetEquipment = usePlannerEditorStoreSafe((s) => s.setEquipment)
  const storeDeploymentOrder = usePlannerEditorStoreSafe((s) => s.deploymentOrder)
  const storeSetDeploymentOrder = usePlannerEditorStoreSafe((s) => s.setDeploymentOrder)
  const storeFilterState = usePlannerEditorStoreSafe((s) => s.deckFilterState)

  // Use override if provided (tracker mode), otherwise use store (editor mode)
  const equipment = equipmentOverride ?? storeEquipment!
  const setEquipment = setEquipmentOverride ?? storeSetEquipment!
  const deploymentOrder = deploymentOrderOverride ?? storeDeploymentOrder!
  const setDeploymentOrder = setDeploymentOrderOverride ?? storeSetDeploymentOrder!

  // Filter state: Use local state in tracker mode, store in editor mode
  const [localFilterState] = useState<DeckFilterState>(createDefaultDeckFilterState)
  const isOverrideMode = equipmentOverride !== undefined
  const filterState = isOverrideMode ? localFilterState : (storeFilterState ?? createDefaultDeckFilterState())

  // Scroll position preservation
  const identityScrollRef = useRef<HTMLDivElement>(null)
  const egoScrollRef = useRef<HTMLDivElement>(null)
  const savedScrollPositionRef = useRef<number>(0)

  // Get equipped IDs for selection display
  const equippedIdentityIds = useMemo(() => {
    return new Set(Object.values(equipment).map((eq) => eq.identity.id))
  }, [equipment])

  const equippedEgoIds = useMemo(() => {
    const ids = new Set<string>()
    Object.values(equipment).forEach((eq) => {
      Object.values(eq.egos).forEach((ego) => {
        if (ego) ids.add(ego.id)
      })
    })
    return ids
  }, [equipment])

  const equippedThreadspinMap = useMemo(() => {
    const map: Record<string, ThreadspinTier> = {}
    Object.values(equipment).forEach((eq) => {
      Object.values(eq.egos).forEach((ego) => {
        if (ego) map[ego.id] = ego.threadspin
      })
    })
    return map
  }, [equipment])

  // Sorting snapshot - captured on mount/open for stable sorting during session
  // Equipped items stay at top even if user unequips them (prevents jarring re-sort)
  const [sortingSnapshot, setSortingSnapshot] = useState<{
    identityIds: Set<string>
    egoIds: Set<string>
    entityMode: string
  } | null>(null)

  // Track previous open state to detect dialog open events
  const prevOpenRef = useRef(open)

  // Capture snapshot when component activates or entity mode changes
  useEffect(() => {
    const isActive = isDialogMode ? open : true
    const justOpened = isDialogMode && open && !prevOpenRef.current
    prevOpenRef.current = open

    if (!isActive) {
      // Dialog closed - clear snapshot for fresh state on next open
      if (sortingSnapshot !== null) {
        setSortingSnapshot(null)
      }
      return
    }

    // Take snapshot if: first time, just opened dialog, or entity mode changed
    const needsSnapshot = sortingSnapshot === null ||
      justOpened ||
      sortingSnapshot.entityMode !== filterState.entityMode

    if (needsSnapshot) {
      setSortingSnapshot({
        identityIds: new Set(equippedIdentityIds),
        egoIds: new Set(equippedEgoIds),
        entityMode: filterState.entityMode,
      })
    }
  }, [isDialogMode, open, filterState.entityMode, equippedIdentityIds, equippedEgoIds, sortingSnapshot])

  // Extract snapshot sets for sorting (fall back to current equipped if no snapshot)
  const sortingIdentityIds = sortingSnapshot?.identityIds ?? equippedIdentityIds
  const sortingEgoIds = sortingSnapshot?.egoIds ?? equippedEgoIds


  // Inactive tab renders nothing until after first paint; latches true until close
  const [hasWarmedInactive, setHasWarmedInactive] = useState(false)

  // Store API for imperative progressive-count updates (no subscription → no re-render here)
  const storeApi = usePlannerEditorStoreApiSafe()

  // Determine if component is "active" for progressive loading
  const isActive = isDialogMode ? open : true

  // Reset progressive state on unmount so the next mount starts cold.
  // Cleanup runs when the dialog closes (DialogContent unmounts).
  // Doing this on unmount (not on mount) ensures the next mount's first
  // render already sees deckVisibleCount=BATCH_SIZE, avoiding a full-list
  // render before a post-commit reset effect can fire.
  useEffect(() => {
    if (!isActive) return
    setHasWarmedInactive(false)
    return () => {
      storeApi?.getState().setDeckVisibleCount(BATCH_SIZE)
    }
  }, [isActive, storeApi])

  // Warm up the inactive tab after first paint, then persist it for the session
  useEffect(() => {
    if (!isActive || hasWarmedInactive) return
    const raf = requestAnimationFrame(() => {
      const ric = (window as typeof window & {
        requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number
        cancelIdleCallback?: (id: number) => void
      }).requestIdleCallback
      if (ric) {
        const id = ric(() => setHasWarmedInactive(true), { timeout: 500 })
        return () => {
          const cic = (window as typeof window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback
          if (cic) cic(id)
        }
      }
      const timer = setTimeout(() => setHasWarmedInactive(true), 100)
      return () => clearTimeout(timer)
    })
    return () => cancelAnimationFrame(raf)
  }, [isActive, hasWarmedInactive])

  // Restore scroll position after equipment changes
  // Effect runs after render, so DOM is ready - no rAF needed
  useEffect(() => {
    if (savedScrollPositionRef.current === 0) return

    const container = filterState.entityMode === 'identity'
      ? identityScrollRef.current
      : egoScrollRef.current

    if (container) {
      container.scrollTop = savedScrollPositionRef.current
      savedScrollPositionRef.current = 0
    }
  }, [equippedIdentityIds, equippedEgoIds, filterState.entityMode])

  // Load identity and EGO data (shared cache)
  const { spec: identitySpec, i18n: identityI18n } = useIdentityListData()
  const { spec: egoSpec, i18n: egoI18n } = useEGOListData()

  // Merge spec and i18n into identity/EGO arrays
  const identities = useMemo<IdentityListItem[]>(() => {
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
  }, [identitySpec, identityI18n])

  const egos = useMemo<EGOListItem[]>(() => {
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
  }, [egoSpec, egoI18n])

  // Get skill data for SinnerGrid
  const skillDataMap = useMemo((): Record<string, SkillData> => {
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
  }, [equipment, identitySpec])

  // Get EGO affinity data
  const egoAffinityMap = useMemo((): Record<string, string> => {
    const map: Record<string, string> = {}
    Object.entries(egoSpec).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  }, [egoSpec])

  // Sort identities ONCE (stable order - sorting doesn't change on filter)
  // Uses snapshot of equipped IDs to keep equipped items at top
  const searchMappings = useSearchMappingsDeferred()

  const sortedIdentities = useMemo(() => {
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
  }, [identities, sortingIdentityIds])

  const sortedEgos = useMemo(() => {
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
  }, [egos, sortingEgoIds])

  // Compute visible IDs based on filters (fast O(n), no React reconciliation)
  // Cards toggle visibility via CSS 'hidden' class
  const visibleIdentityIds = useMemo(() => {
    const ids = new Set<string>()
    for (const identity of sortedIdentities) {
      if (!matchesDeckFilter(identity, filterState, 'identity', searchMappings)) continue
      ids.add(identity.id)
    }
    return ids
  }, [sortedIdentities, filterState, searchMappings])

  const visibleEgoIds = useMemo(() => {
    const ids = new Set<string>()
    for (const ego of sortedEgos) {
      if (!matchesDeckFilter(ego, filterState, 'ego', searchMappings)) continue
      ids.add(ego.id)
    }
    return ids
  }, [sortedEgos, filterState, searchMappings])

  // Progressive rendering: render cards incrementally
  const totalIdentities = sortedIdentities.length
  const totalEgos = sortedEgos.length

  // Progressive loading via rAF chain - imperative store writes (no subscription here)
  useEffect(() => {
    if (!isActive || !storeApi) return
    const totalCount = Math.max(totalIdentities, totalEgos)
    let raf: number | null = null

    const tick = () => {
      const current = storeApi.getState().deckVisibleCount
      if (current >= totalCount) return
      storeApi.getState().setDeckVisibleCount(Math.min(current + BATCH_SIZE, totalCount))
      raf = requestAnimationFrame(tick)
    }

    raf = requestAnimationFrame(tick)
    return () => {
      if (raf !== null) cancelAnimationFrame(raf)
    }
  }, [isActive, totalIdentities, totalEgos, storeApi])

  // Construct deckState for StatusViewer
  const deckState: DeckState = useMemo(() => ({
    equipment,
    deploymentOrder,
    deploymentConfig: {
      maxDeployed: DEFAULT_DEPLOYMENT_MAX,
    },
  }), [equipment, deploymentOrder])

  // Create EGO lookup map
  const egoMap = useMemo(() => {
    const map: Record<string, EGOListItem> = {}
    egos.forEach((e) => { map[e.id] = e })
    return map
  }, [egos])

  // Handlers
  const handleToggleDeploy = useCallback((sinnerIndex: number) => {
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
  }, [deploymentOrder, setDeploymentOrder])

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
      {/* Sinner Grid */}
      <div className={SECTION_STYLES.container}>
        {filterState.entityMode === 'identity' ? (
          <CompactIdentityRow
            equipment={equipment}
            deploymentOrder={deploymentOrder}
            skillDataMap={skillDataMap}
            onToggleDeploy={handleToggleDeploy}
          />
        ) : (
          <CompactEgoGrid
            equipment={equipment}
            egoAffinityMap={egoAffinityMap}
          />
        )}
        {/* Status + Action Bar row */}
        <div className="mt-3 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
          <StatusViewer deckState={deckState} />
          <DeckBuilderActionBar
            onImport={onImport}
            onExport={onExport}
            onResetOrder={onResetOrder}
          />
        </div>
      </div>

      {/* Entity Toggle and List */}
      <div className={`${SECTION_STYLES.container} space-y-4`}>
        <DeckFilterBar />

        {/* Grids own their progressive-render state via deckVisibleCount store subscription */}
        {/* Cards are rendered once and filtered via CSS - no React reconciliation on filter changes */}
        {(filterState.entityMode === 'identity' || hasWarmedInactive) && (
          <IdentityGrid
            sortedIdentities={sortedIdentities}
            visibleIds={visibleIdentityIds}
            equippedIds={equippedIdentityIds}
            onEquip={handleEquipIdentity}
            scrollRef={identityScrollRef}
            isActive={filterState.entityMode === 'identity'}
          />
        )}

        {(filterState.entityMode === 'ego' || hasWarmedInactive) && (
          <EgoGrid
            sortedEgos={sortedEgos}
            visibleIds={visibleEgoIds}
            equippedIds={equippedEgoIds}
            equippedThreadspinMap={equippedThreadspinMap}
            onEquip={handleEquipEgo}
            onUnequip={handleUnequipEgo}
            scrollRef={egoScrollRef}
            isActive={filterState.entityMode === 'ego'}
          />
        )}
      </div>
    </div>
  )
}
