import { useMemo, startTransition, useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { MAX_LEVEL, DEFAULT_DEPLOYMENT_MAX, SECTION_STYLES, CARD_GRID, EGO_TYPES } from '@/lib/constants'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { useEGOListData } from '@/hooks/useEGOListData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import type { SinnerEquipment, UptieTier, ThreadspinTier, DeckState, DeckFilterState, EntityMode } from '@/types/DeckTypes'
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

interface DeckBuilderPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  equipment: Record<string, SinnerEquipment>
  setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>
  deploymentOrder: number[]
  setDeploymentOrder: React.Dispatch<React.SetStateAction<number[]>>
  filterState: DeckFilterState
  setFilterState: React.Dispatch<React.SetStateAction<DeckFilterState>>
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
}

/**
 * Dialog for editing deck equipment and deployment.
 * Uses lifted filter state for persistence across open/close.
 */
export function DeckBuilderPane({
  open,
  onOpenChange,
  equipment,
  setEquipment,
  deploymentOrder,
  setDeploymentOrder,
  filterState,
  setFilterState,
  onImport,
  onExport,
  onResetOrder,
}: DeckBuilderPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Scroll position preservation
  const identityScrollRef = useRef<HTMLDivElement>(null)
  const egoScrollRef = useRef<HTMLDivElement>(null)
  const savedScrollPositionRef = useRef<number>(0)

  // Sorting snapshot state - capture equipped IDs when pane opens to prevent real-time re-sorting
  // Uses state (not refs) so useMemo can depend on it and recalculate when snapshot updates
  const [sortingIdentityIds, setSortingIdentityIds] = useState<Set<string>>(() => new Set())
  const [sortingEgoIds, setSortingEgoIds] = useState<Set<string>>(() => new Set())
  const snapshotEntityModeRef = useRef<string | null>(null)
  const hasSnapshotRef = useRef(false)

  // Get equipped IDs for selection display (must be before useEffects that depend on them)
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

  // Capture sorting snapshot when pane opens or tab switches, reset when pane closes
  useEffect(() => {
    if (open) {
      const needsSnapshot = !hasSnapshotRef.current || snapshotEntityModeRef.current !== filterState.entityMode
      if (needsSnapshot) {
        // Take snapshot of current equipped IDs for stable sorting
        setSortingIdentityIds(new Set(equippedIdentityIds))
        setSortingEgoIds(new Set(equippedEgoIds))
        snapshotEntityModeRef.current = filterState.entityMode
        hasSnapshotRef.current = true
      }
    } else if (hasSnapshotRef.current) {
      // Reset snapshot when pane closes so next open gets fresh snapshot
      hasSnapshotRef.current = false
      snapshotEntityModeRef.current = null
    }
  }, [open, filterState.entityMode, equippedIdentityIds, equippedEgoIds])

  // Defer content loading so dialog opens instantly (only first time)
  const [contentReady, setContentReady] = useState(false)
  useEffect(() => {
    if (open && !contentReady) {
      // Delay content render to next frame so dialog animation starts first
      const frame = requestAnimationFrame(() => setContentReady(true))
      return () => cancelAnimationFrame(frame)
    }
  }, [open, contentReady])

  // Progressive rendering - render items in batches to avoid blocking main thread
  const BATCH_SIZE = 10
  const [visibleCount, setVisibleCount] = useState(BATCH_SIZE)

  // Create stable filter key for dependency comparison (Sets cause reference issues)
  // Note: entityMode excluded so tab switch doesn't reset progressive loading
  const filterKey = useMemo(() => {
    const sinners = Array.from(filterState.selectedSinners).sort().join(',')
    const keywords = Array.from(filterState.selectedKeywords).sort().join(',')
    return `${sinners}|${keywords}|${filterState.searchQuery}`
  }, [filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery])

  useEffect(() => {
    // Reset visible count when pane opens or filter changes
    if (open) {
      setVisibleCount(BATCH_SIZE)
    }
  }, [open, filterKey])

  // Restore scroll position after equipment changes
  useEffect(() => {
    const container = filterState.entityMode === 'identity' ? identityScrollRef.current : egoScrollRef.current
    if (container && savedScrollPositionRef.current > 0) {
      // Capture position before clearing to prevent multiple restorations
      const position = savedScrollPositionRef.current
      savedScrollPositionRef.current = 0

      // Use requestAnimationFrame to ensure DOM has updated
      requestAnimationFrame(() => {
        container.scrollTop = position
      })
    }
  }, [equippedIdentityIds, equippedEgoIds, filterState.entityMode])

  // Load identity and EGO data (shared cache)
  const { spec: identitySpec, i18n: identityI18n } = useIdentityListData()
  const { spec: egoSpec, i18n: egoI18n } = useEGOListData()

  // Merge spec and i18n into identity/EGO arrays (skip until content ready)
  const identities = useMemo<IdentityListItem[]>(() => {
    if (!contentReady) return []
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
  }, [contentReady, identitySpec, identityI18n])

  const egos = useMemo<EGOListItem[]>(() => {
    if (!contentReady) return []
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
  }, [contentReady, egoSpec, egoI18n])

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

  // Filter and sort identities
  const { keywordToValue, unitKeywordToValue } = useSearchMappings()

  const filteredAndSortedIdentities = useMemo(() => {
    const filtered = identities.filter((identity) => {
      // Sinner filter
      if (filterState.selectedSinners.size > 0 && !filterState.selectedSinners.has(getSinnerFromId(identity.id))) {
        return false
      }
      // Keyword filter
      if (filterState.selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(filterState.selectedKeywords).every((kw) => identity.skillKeywordList.includes(kw))
        if (!hasAllKeywords) return false
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
        if (!nameMatch && !keywordMatch && !unitKeywordMatch) return false
      }
      return true
    })
    // Sort: equipped first, then by release date criteria (updateDate DESC → rank DESC → id DESC)
    return filtered.sort((a, b) => {
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
  }, [identities, filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery, keywordToValue, unitKeywordToValue, sortingIdentityIds])

  const filteredAndSortedEgos = useMemo(() => {
    const filtered = egos.filter((ego) => {
      // Sinner filter
      if (filterState.selectedSinners.size > 0 && !filterState.selectedSinners.has(getSinnerFromId(ego.id))) {
        return false
      }
      // Keyword filter
      if (filterState.selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(filterState.selectedKeywords).every((kw) => ego.skillKeywordList.includes(kw as Keyword))
        if (!hasAllKeywords) return false
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
        if (!nameMatch && !keywordMatch) return false
      }
      return true
    })
    // Sort: equipped first, then by release date criteria (updateDate DESC → egoType tier DESC → sinner DESC → id DESC)
    return filtered.sort((a, b) => {
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
  }, [egos, filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery, keywordToValue, sortingEgoIds])

  // Get current list based on entity mode
  const currentList = filterState.entityMode === 'identity' ? filteredAndSortedIdentities : filteredAndSortedEgos
  const totalCount = currentList.length

  // Progressive loading effect - load more items until all are visible
  useEffect(() => {
    if (!contentReady || visibleCount >= totalCount) return

    const frame = requestAnimationFrame(() => {
      setVisibleCount((prev) => Math.min(prev + BATCH_SIZE, totalCount))
    })

    return () => cancelAnimationFrame(frame)
  }, [contentReady, visibleCount, totalCount])

  // Slice arrays for progressive rendering
  const visibleIdentities = useMemo(
    () => filteredAndSortedIdentities.slice(0, filterState.entityMode === 'identity' ? visibleCount : filteredAndSortedIdentities.length),
    [filteredAndSortedIdentities, visibleCount, filterState.entityMode]
  )
  const visibleEgos = useMemo(
    () => filteredAndSortedEgos.slice(0, filterState.entityMode === 'ego' ? visibleCount : filteredAndSortedEgos.length),
    [filteredAndSortedEgos, visibleCount, filterState.entityMode]
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
      setDeploymentOrder((prev) => {
        const currentIndex = prev.indexOf(sinnerIndex)
        if (currentIndex >= 0) {
          const newOrder = [...prev]
          newOrder.splice(currentIndex, 1)
          return newOrder
        } else {
          return [...prev, sinnerIndex]
        }
      })
    })
  }

  const handleEquipIdentity = (identityId: string, data: { uptie?: UptieTier; level?: number }) => {
    // Save scroll position before state update
    if (identityScrollRef.current) {
      savedScrollPositionRef.current = identityScrollRef.current.scrollTop
    }

    const sinnerCode = getSinnerCodeFromId(identityId)
    startTransition(() => {
      setEquipment((prev) => {
        const sinnerEquipment = prev[sinnerCode]
        if (!sinnerEquipment) return prev
        return {
          ...prev,
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
      setEquipment((prev) => {
        const sinnerEquipment = prev[sinnerCode]
        if (!sinnerEquipment || !ego) return prev
        const rank = ego.egoType
        return {
          ...prev,
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
      setEquipment((prev) => {
        const sinnerEquipment = prev[sinnerCode]
        if (!sinnerEquipment || !ego) return prev
        const rank = ego.egoType
        // ZAYIN cannot be unequipped
        if (rank === 'ZAYIN') return prev
        const newEgos = { ...sinnerEquipment.egos }
        delete newEgos[rank]
        return {
          ...prev,
          [sinnerCode]: {
            ...sinnerEquipment,
            egos: newEgos,
          },
        }
      })
    })
  }

  const handleEntityModeChange = (mode: EntityMode) => {
    startTransition(() => {
      setFilterState((prev) => ({ ...prev, entityMode: mode }))
    })
  }

  const handleSinnersChange = (sinners: Set<string>) => {
    startTransition(() => {
      setFilterState((prev) => ({ ...prev, selectedSinners: sinners }))
    })
  }

  const handleKeywordsChange = (keywords: Set<string>) => {
    startTransition(() => {
      setFilterState((prev) => ({ ...prev, selectedKeywords: keywords }))
    })
  }

  const handleSearchChange = (query: string) => {
    startTransition(() => {
      setFilterState((prev) => ({ ...prev, searchQuery: query }))
    })
  }

  return (
    <>
      {/* Custom backdrop to block background interaction */}
      {open && (
        <div
          className="fixed inset-0 z-50 bg-black/50 animate-in fade-in-0"
          onClick={() => onOpenChange(false)}
        />
      )}

      <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
        <DialogContent
          className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col duration-100"
          {...(contentReady && { forceMount: true })}
          onPointerDownOutside={(e) => e.preventDefault()}
        >
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <DialogTitle>{t('deckBuilder.paneTitle')}</DialogTitle>
        </DialogHeader>

        {/* Scrollable content area with visual margin */}
        <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6 space-y-6">
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
            <div className="flex gap-4 justify-between flex-wrap">
              {/* Left side: Toggle and Filters */}
              <div className="flex gap-4 items-center flex-wrap">
                <EntityToggle mode={filterState.entityMode} onModeChange={handleEntityModeChange} />
                <SinnerFilter
                  selectedSinners={filterState.selectedSinners}
                  onSelectionChange={handleSinnersChange}
                />
                <KeywordFilter
                  selectedKeywords={filterState.selectedKeywords}
                  onSelectionChange={handleKeywordsChange}
                />
              </div>
              {/* Right side: Search bar */}
              <div className="shrink-0">
                <SearchBar
                  searchQuery={filterState.searchQuery}
                  onSearchChange={handleSearchChange}
                  placeholder="Search..."
                />
              </div>
            </div>

            {/* Both tabs rendered, hidden with CSS to preserve DOM and image cache */}
            <div className={filterState.entityMode === 'identity' ? '' : 'hidden'}>
              <div ref={identityScrollRef} className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
                <div className="pt-4">
                  <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
                    {visibleIdentities.map((identity) => {
                      const isSelected = equippedIdentityIds.has(identity.id)
                      return (
                        <TierLevelSelector
                          key={identity.id}
                          mode="identity"
                          entityId={identity.id}
                          currentUptie={4}
                          currentLevel={MAX_LEVEL}
                          isSelected={isSelected}
                          onConfirm={handleEquipIdentity}
                        >
                          <IdentityCard
                            identity={identity}
                            overlay={isSelected ? (
                              <img
                                src={getSelectedIndicatorPath()}
                                alt="Selected"
                                className="absolute inset-0 m-auto w-38 object-contain pointer-events-none"
                              />
                            ) : undefined}
                          />
                        </TierLevelSelector>
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
                    {visibleEgos.map((ego) => {
                      const isSelected = equippedEgoIds.has(ego.id)
                      return (
                        <TierLevelSelector
                          key={ego.id}
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
                            overlay={isSelected ? (
                              <img
                                src={getSelectedIndicatorPath()}
                                alt="Selected"
                                className="absolute inset-0 m-auto w-28 object-contain pointer-events-none"
                              />
                            ) : undefined}
                          />
                        </TierLevelSelector>
                      )
                    })}
                  </ResponsiveCardGrid>
                </div>
              </div>
            </div>
          </div>
        </div>

        <DialogFooter className="shrink-0 border-t border-border pt-4">
          <Button onClick={() => { onOpenChange(false) }}>
            {t('plannerMD.done')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  )
}
