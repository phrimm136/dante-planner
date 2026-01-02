import { useMemo, startTransition, useDeferredValue, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { MAX_LEVEL, DEFAULT_DEPLOYMENT_MAX, SECTION_STYLES, CARD_GRID } from '@/lib/constants'
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
import type { Identity } from '@/types/IdentityTypes'
import type { EGO } from '@/types/EGOTypes'
import { getSinnerFromId } from '@/lib/utils'
import { SinnerGrid, type SkillData } from './SinnerGrid'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'
import { EntityToggle } from './EntityToggle'
import { TierLevelSelector } from './TierLevelSelector'
import { IdentityCard } from '@/components/identity/IdentityCard'
import { EGOCard } from '@/components/ego/EGOCard'
import { SinnerFilter } from '@/components/common/SinnerFilter'
import { KeywordFilter } from '@/components/common/KeywordFilter'
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
  const { t } = useTranslation()

  // Defer content loading so dialog opens instantly
  const [contentReady, setContentReady] = useState(false)
  useEffect(() => {
    if (open) {
      // Delay content render to next frame so dialog animation starts first
      const frame = requestAnimationFrame(() => setContentReady(true))
      return () => cancelAnimationFrame(frame)
    } else {
      setContentReady(false)
    }
  }, [open])

  // Load identity and EGO data (shared cache)
  const { spec: identitySpec, i18n: identityI18n } = useIdentityListData()
  const { spec: egoSpec, i18n: egoI18n } = useEGOListData()

  // Merge spec and i18n into Identity/EGO arrays (skip until content ready)
  const identities = useMemo<Identity[]>(() => {
    if (!contentReady) return []
    return Object.entries(identitySpec).map(([id, specData]) => ({
      id,
      name: identityI18n[id] || id,
      rank: specData.rank,
      unitKeywordList: specData.unitKeywordList,
      skillKeywordList: specData.skillKeywordList,
    }))
  }, [contentReady, identitySpec, identityI18n])

  const egos = useMemo<EGO[]>(() => {
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
        const nameMatch = identity.name.toLowerCase().includes(lowerQuery)
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
    // Sort: equipped first
    return filtered.sort((a, b) => {
      const aEquipped = equippedIdentityIds.has(a.id) ? 0 : 1
      const bEquipped = equippedIdentityIds.has(b.id) ? 0 : 1
      return aEquipped - bEquipped
    })
  }, [identities, filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery, keywordToValue, unitKeywordToValue, equippedIdentityIds])

  const filteredAndSortedEgos = useMemo(() => {
    const filtered = egos.filter((ego) => {
      // Sinner filter
      if (filterState.selectedSinners.size > 0 && !filterState.selectedSinners.has(getSinnerFromId(ego.id))) {
        return false
      }
      // Keyword filter
      if (filterState.selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(filterState.selectedKeywords).every((kw) => ego.skillKeywordList.includes(kw))
        if (!hasAllKeywords) return false
      }
      // Search filter
      if (filterState.searchQuery) {
        const lowerQuery = filterState.searchQuery.toLowerCase()
        const nameMatch = ego.name.toLowerCase().includes(lowerQuery)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, bracketedValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return bracketedValues.some((bv) => ego.skillKeywordList.includes(bv))
          }
          return false
        })
        if (!nameMatch && !keywordMatch) return false
      }
      return true
    })
    // Sort: equipped first
    return filtered.sort((a, b) => {
      const aEquipped = equippedEgoIds.has(a.id) ? 0 : 1
      const bEquipped = equippedEgoIds.has(b.id) ? 0 : 1
      return aEquipped - bEquipped
    })
  }, [egos, filterState.selectedSinners, filterState.selectedKeywords, filterState.searchQuery, keywordToValue, equippedEgoIds])

  // Defer heavy entity lists so dialog opens instantly
  const deferredIdentities = useDeferredValue(filteredAndSortedIdentities)
  const deferredEgos = useDeferredValue(filteredAndSortedEgos)

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
    const map: Record<string, EGO> = {}
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
    const sinner = getSinnerFromId(identityId)
    startTransition(() => {
      setEquipment((prev) => {
        const sinnerEquipment = prev[sinner]
        if (!sinnerEquipment) return prev
        return {
          ...prev,
          [sinner]: {
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
    const sinner = getSinnerFromId(egoId)
    const ego = egoMap[egoId]
    startTransition(() => {
      setEquipment((prev) => {
        const sinnerEquipment = prev[sinner]
        if (!sinnerEquipment || !ego) return prev
        const rank = ego.egoType
        return {
          ...prev,
          [sinner]: {
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col duration-100">
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <DialogTitle>{t('deckBuilder.paneTitle')}</DialogTitle>
        </DialogHeader>

        {/* Scrollable content area with visual margin */}
        <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6 space-y-6">
          {/* Sinner Grid */}
          <div className={SECTION_STYLES.container}>
            <h3 className={`${SECTION_STYLES.TEXT.subHeader} mb-3`}>Formation</h3>
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
              <div className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
                <div className="pt-4">
                  <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
                    {deferredIdentities.map((identity) => {
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
                          <IdentityCard identity={identity} isSelected={isSelected} />
                        </TierLevelSelector>
                      )
                    })}
                  </ResponsiveCardGrid>
                </div>
              </div>
            </div>

            <div className={filterState.entityMode === 'ego' ? '' : 'hidden'}>
              <div className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
                <div className="pt-4">
                  <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO}>
                    {deferredEgos.map((ego) => {
                      const isSelected = equippedEgoIds.has(ego.id)
                      return (
                        <TierLevelSelector
                          key={ego.id}
                          mode="ego"
                          entityId={ego.id}
                          currentThreadspin={4}
                          isSelected={isSelected}
                          onConfirm={handleEquipEgo}
                        >
                          <EGOCard ego={ego} isSelected={isSelected} />
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
  )
}
