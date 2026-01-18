import { useMemo, startTransition, useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { MAX_LEVEL, DEFAULT_DEPLOYMENT_MAX, SECTION_STYLES, CARD_GRID, EGO_TYPES } from '@/lib/constants'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { useEGOListData } from '@/hooks/useEGOListData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import type { UptieTier, ThreadspinTier, DeckState, EntityMode } from '@/types/DeckTypes'
import type { IdentityListItem } from '@/types/IdentityTypes'
import type { EGOListItem } from '@/types/EGOTypes'
import type { Keyword } from '@/lib/constants'
import { getSinnerFromId, getSinnerCodeFromId } from '@/lib/utils'
import { getSelectedIndicatorPath } from '@/lib/assetPaths'
import { SinnerGrid, type SkillData } from './SinnerGrid'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'
import { EntityToggle } from './EntityToggle'
import { TierLevelSelector } from './TierLevelSelector'
import { IdentityCard } from '@/components/identity/IdentityCard'
import { EGOCard } from '@/components/ego/EGOCard'
import { SinnerFilter } from '@/components/filter/SinnerFilter'
import { KeywordFilter } from '@/components/filter/KeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'

/** Base props shared by both modes */
interface DeckBuilderContentBaseProps {
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
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
  const { onImport, onExport, onResetOrder } = props
  const isDialogMode = props.mode === 'dialog'
  const open = isDialogMode ? props.open : true
  // Store state
  const equipment = usePlannerEditorStore((s) => s.equipment)
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const filterState = usePlannerEditorStore((s) => s.deckFilterState)
  const setFilterState = usePlannerEditorStore((s) => s.setDeckFilterState)
  const { t } = useTranslation(['planner', 'common'])

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


  // Progressive rendering - render items in batches to avoid blocking main thread
  const [visibleCount, setVisibleCount] = useState(BATCH_SIZE)

  // Determine if component is "active" for progressive loading
  const isActive = isDialogMode ? open : true

  // Reset visible count only when component becomes active (not on filter changes)
  // Filter changes use CSS hiding pattern instead of re-rendering
  useEffect(() => {
    if (isActive) {
      setVisibleCount(BATCH_SIZE)
    }
  }, [isActive])

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
      attributeTypes: specData.attributeType,
      atkTypes: specData.atkType,
      season: specData.season,
    }))
  }, [identitySpec, identityI18n])

  const egos = useMemo<EGOListItem[]>(() => {
    return Object.entries(egoSpec).map(([id, specData]) => ({
      id,
      name: egoI18n[id] || id,
      egoType: specData.egoType,
      skillKeywordList: specData.skillKeywordList,
      attributeTypes: specData.attributeType,
      atkTypes: specData.atkType,
      updateDate: specData.updateDate,
      season: specData.season,
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
  const { keywordToValue, unitKeywordToValue } = useSearchMappings()

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
      // Sinner filter
      if (filterState.selectedSinners.size > 0 && !filterState.selectedSinners.has(getSinnerFromId(identity.id))) {
        continue
      }
      // Keyword filter
      if (filterState.selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(filterState.selectedKeywords).every((kw) => identity.skillKeywordList.includes(kw))
        if (!hasAllKeywords) continue
      }
      // Search filter
      if (filterState.searchQuery) {
        const lowerQuery = filterState.searchQuery.toLowerCase()
        const nameMatch = identity.name?.toLowerCase().includes(lowerQuery) ?? false
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, internalCodes]) => {
          if (naturalLang.includes(lowerQuery)) {
            return internalCodes.some((code) => identity.skillKeywordList.includes(code))
          }
          return false
        })
        const unitKeywordMatch = Array.from(unitKeywordToValue.entries()).some(([naturalLang, internalCodes]) => {
          if (naturalLang.includes(lowerQuery)) {
            return internalCodes.some((code) => identity.unitKeywordList.includes(code))
          }
          return false
        })
        if (!nameMatch && !keywordMatch && !unitKeywordMatch) continue
      }
      ids.add(identity.id)
    }
    return ids
  }, [sortedIdentities, filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery, keywordToValue, unitKeywordToValue])

  const visibleEgoIds = useMemo(() => {
    const ids = new Set<string>()
    for (const ego of sortedEgos) {
      // Sinner filter
      if (filterState.selectedSinners.size > 0 && !filterState.selectedSinners.has(getSinnerFromId(ego.id))) {
        continue
      }
      // Keyword filter
      if (filterState.selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(filterState.selectedKeywords).every((kw) => ego.skillKeywordList.includes(kw as Keyword))
        if (!hasAllKeywords) continue
      }
      // Search filter
      if (filterState.searchQuery) {
        const lowerQuery = filterState.searchQuery.toLowerCase()
        const nameMatch = ego.name?.toLowerCase().includes(lowerQuery) ?? false
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, bracketedValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return bracketedValues.some((bv) => ego.skillKeywordList.includes(bv as Keyword))
          }
          return false
        })
        if (!nameMatch && !keywordMatch) continue
      }
      ids.add(ego.id)
    }
    return ids
  }, [sortedEgos, filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery, keywordToValue])

  // Progressive rendering: render cards incrementally
  const totalIdentities = sortedIdentities.length
  const totalEgos = sortedEgos.length

  // Progressive loading effect - load more items until all are visible
  useEffect(() => {
    const totalCount = filterState.entityMode === 'identity' ? totalIdentities : totalEgos
    if (visibleCount >= totalCount) return

    let mounted = true
    const frame = requestAnimationFrame(() => {
      if (mounted) {
        setVisibleCount((prev) => Math.min(prev + BATCH_SIZE, totalCount))
      }
    })

    return () => {
      mounted = false
      cancelAnimationFrame(frame)
    }
  }, [visibleCount, totalIdentities, totalEgos, filterState.entityMode])

  // Slice arrays for progressive rendering
  const displayIdentities = useMemo(
    () => sortedIdentities.slice(0, filterState.entityMode === 'identity' ? visibleCount : sortedIdentities.length),
    [sortedIdentities, visibleCount, filterState.entityMode]
  )
  const displayEgos = useMemo(
    () => sortedEgos.slice(0, filterState.entityMode === 'ego' ? visibleCount : sortedEgos.length),
    [sortedEgos, visibleCount, filterState.entityMode]
  )

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
    startTransition(() => {
      const sinnerEquipment = equipment[sinnerCode]
      if (!sinnerEquipment) return
      setEquipment({
        ...equipment,
        [sinnerCode]: {
          ...sinnerEquipment,
          identity: {
            id: identityId,
            uptie: data.uptie || 4,
            level: data.level || MAX_LEVEL,
          },
        },
      })
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
      const sinnerEquipment = equipment[sinnerCode]
      if (!sinnerEquipment || !ego) return
      const rank = ego.egoType
      setEquipment({
        ...equipment,
        [sinnerCode]: {
          ...sinnerEquipment,
          egos: {
            ...sinnerEquipment.egos,
            [rank]: {
              id: egoId,
              threadspin: data.threadspin || 4,
            },
          },
        },
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
      const sinnerEquipment = equipment[sinnerCode]
      if (!sinnerEquipment || !ego) return
      const rank = ego.egoType
      // ZAYIN cannot be unequipped
      if (rank === 'ZAYIN') return
      const newEgos = { ...sinnerEquipment.egos }
      delete newEgos[rank]
      setEquipment({
        ...equipment,
        [sinnerCode]: {
          ...sinnerEquipment,
          egos: newEgos,
        },
      })
    })
  }

  const handleEntityModeChange = (mode: EntityMode) => {
    startTransition(() => {
      setFilterState({ ...filterState, entityMode: mode })
    })
  }

  const handleSinnersChange = (sinners: Set<string>) => {
    startTransition(() => {
      setFilterState({ ...filterState, selectedSinners: sinners })
    })
  }

  const handleKeywordsChange = (keywords: Set<string>) => {
    startTransition(() => {
      setFilterState({ ...filterState, selectedKeywords: keywords })
    })
  }

  const handleSearchChange = (query: string) => {
    startTransition(() => {
      setFilterState({ ...filterState, searchQuery: query })
    })
  }

  return (
    <div className="space-y-6">
      {/* Sinner Grid */}
      <div className={SECTION_STYLES.container}>
        <SinnerGrid
          equipment={equipment}
          deploymentOrder={deploymentOrder}
          identities={identities}
          skillDataMap={skillDataMap}
          egoAffinityMap={egoAffinityMap}
          onToggleDeploy={handleToggleDeploy}
        />
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
        <div className="flex flex-col sm:flex-row gap-4 sm:justify-between">
          {/* Left side: Toggle and Filters */}
          <div className="flex flex-col sm:flex-row gap-4 sm:items-center min-w-0">
            <EntityToggle mode={filterState.entityMode} onModeChange={handleEntityModeChange} />
            <div className="min-w-0">
              <SinnerFilter
                selectedSinners={filterState.selectedSinners}
                onSelectionChange={handleSinnersChange}
              />
            </div>
            <div className="min-w-0">
              <KeywordFilter
                selectedKeywords={filterState.selectedKeywords}
                onSelectionChange={handleKeywordsChange}
              />
            </div>
          </div>
          {/* Right side: Search bar */}
          <div className="min-w-0 sm:shrink-0">
            <SearchBar
              searchQuery={filterState.searchQuery}
              onSearchChange={handleSearchChange}
              placeholder={filterState.entityMode === 'identity'
                ? t('deckBuilder.identitySearchPlaceholder')
                : t('deckBuilder.egoSearchPlaceholder')}
            />
          </div>
        </div>

        {/* Both tabs rendered, toggle visibility via CSS 'hidden' class */}
        {/* Cards are rendered once and filtered via CSS - no React reconciliation on filter changes */}
        <div className={filterState.entityMode === 'identity' ? '' : 'hidden'}>
          <div ref={identityScrollRef} className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
            <div className="pt-4">
              <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
                {displayIdentities.map((identity) => {
                  const isSelected = equippedIdentityIds.has(identity.id)
                  const isVisible = visibleIdentityIds.has(identity.id)
                  return (
                    <div key={identity.id} className={isVisible ? '' : 'hidden'}>
                      <TierLevelSelector
                        mode="identity"
                        entityId={identity.id}
                        currentUptie={4}
                        currentLevel={MAX_LEVEL}
                        isSelected={isSelected}
                        onConfirm={handleEquipIdentity}
                      >
                        <IdentityCard
                          identity={identity}
                          isSelected={isSelected}
                          overlay={isSelected ? (
                            <img
                              src={getSelectedIndicatorPath()}
                              alt="Selected"
                              className="absolute inset-0 m-auto w-38 object-contain pointer-events-none"
                            />
                          ) : undefined}
                        />
                      </TierLevelSelector>
                    </div>
                  )
                })}
              </ResponsiveCardGrid>
            </div>
          </div>
        </div>

        <div className={filterState.entityMode === 'ego' ? '' : 'hidden'}>
          <div ref={egoScrollRef} className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
            <div className="pt-4">
              <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO}>
                {displayEgos.map((ego) => {
                  const isSelected = equippedEgoIds.has(ego.id)
                  const isVisible = visibleEgoIds.has(ego.id)
                  return (
                    <div key={ego.id} className={isVisible ? '' : 'hidden'}>
                      <TierLevelSelector
                        mode="ego"
                        entityId={ego.id}
                        currentThreadspin={4}
                        isSelected={isSelected}
                        egoType={ego.egoType}
                        onConfirm={handleEquipEgo}
                        onUnequip={handleUnequipEgo}
                      >
                        <EGOCard
                          ego={ego}
                          isSelected={isSelected}
                          overlay={isSelected ? (
                            <img
                              src={getSelectedIndicatorPath()}
                              alt="Selected"
                              className="absolute inset-0 m-auto w-28 object-contain pointer-events-none"
                            />
                          ) : undefined}
                        />
                      </TierLevelSelector>
                    </div>
                  )
                })}
              </ResponsiveCardGrid>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
