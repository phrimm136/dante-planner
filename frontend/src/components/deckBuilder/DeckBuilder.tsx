import React, { useState, useCallback, useMemo, startTransition, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Upload, Download } from 'lucide-react'
import { MAX_LEVEL, SINNERS, DEFAULT_DEPLOYMENT_MAX } from '@/lib/constants'
import { useEntityListData } from '@/hooks/useEntityListData'
import { useIdentitySpecData, useEGOSpecData } from '@/hooks/useSpecData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { encodeDeckCode, decodeDeckCode, validateDeckCode, type DecodedDeck } from '@/lib/deckCode'
import type { SinnerEquipment, UptieTier, ThreadspinTier, DeckState } from '@/types/DeckTypes'
import type { Identity } from '@/types/IdentityTypes'
import type { EGO, EGORank } from '@/types/EGOTypes'
import { SinnerGrid, type SkillData } from './SinnerGrid'
import { StatusViewer } from './StatusViewer'
import { EntityToggle, type EntityMode } from './EntityToggle'
import { TierLevelSelector } from './TierLevelSelector'
import { IdentityCard } from '@/components/identity/IdentityCard'
import { EGOCard } from '@/components/ego/EGOCard'
import { SinnerFilter } from '@/components/common/SinnerFilter'
import { KeywordFilter } from '@/components/common/KeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { getSinnerFromId } from '@/lib/utils'

// Generate default equipment for all sinners
function createDefaultEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}

  SINNERS.forEach((sinner, index) => {
    const sinnerIdPart = (index + 1).toString().padStart(2, '0')
    const defaultIdentityId = `1${sinnerIdPart}01`
    const defaultEgoId = `2${sinnerIdPart}01`

    equipment[sinner] = {
      identity: {
        id: defaultIdentityId,
        uptie: 4,
        level: MAX_LEVEL,
      },
      egos: {
        ZAYIN: {
          id: defaultEgoId,
          threadspin: 4,
        },
      },
    }
  })

  return equipment
}

export const DeckBuilder: React.FC = () => {
  const { t } = useTranslation()

  // Separate states for better memoization - equipment and deploymentOrder are independent
  const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>(createDefaultEquipment)
  const [deploymentOrder, setDeploymentOrder] = useState<number[]>([])

  // Entity mode toggle
  const [entityMode, setEntityMode] = useState<EntityMode>('identity')

  // Filter states
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Import dialog state
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  // Load identity and EGO lists
  const { data: identities = [], isPending: identitiesPending } = useEntityListData<Identity>('identity')
  const { data: egos = [], isPending: egosPending } = useEntityListData<EGO>('ego')
  const { data: identitySpecMap } = useIdentitySpecData()
  const { data: egoSpecMap } = useEGOSpecData()

  // Get skill data (affinities and attack types) for each equipped identity
  const skillDataMap = useMemo((): Record<string, SkillData> => {
    if (!identitySpecMap) return {}
    const map: Record<string, SkillData> = {}
    Object.values(equipment).forEach((eq) => {
      const spec = identitySpecMap[eq.identity.id]
      if (spec) {
        map[eq.identity.id] = {
          affinities: spec.attributeType?.slice(0, 3) ?? [],
          atkTypes: spec.atkType?.slice(0, 3) ?? [],
        }
      }
    })
    return map
  }, [equipment, identitySpecMap])

  // Get EGO affinity data (first affinity for background color)
  const egoAffinityMap = useMemo((): Record<string, string> => {
    if (!egoSpecMap) return {}
    const map: Record<string, string> = {}
    Object.entries(egoSpecMap).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  }, [egoSpecMap])

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
  const { keywordToValue, traitToValue } = useSearchMappings()

  const filteredAndSortedIdentities = useMemo(() => {
    const filtered = identities.filter((identity) => {
      // Sinner filter
      if (selectedSinners.size > 0 && !selectedSinners.has(getSinnerFromId(identity.id))) {
        return false
      }
      // Keyword filter
      if (selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(selectedKeywords).every((kw) => identity.skillKeywordList.includes(kw))
        if (!hasAllKeywords) return false
      }
      // Search filter
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = identity.name.toLowerCase().includes(lowerQuery)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, bracketedValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return bracketedValues.some((bv) => identity.skillKeywordList.includes(bv))
          }
          return false
        })
        const traitMatch = Array.from(traitToValue.entries()).some(([naturalLang, bracketedValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return bracketedValues.some((bv) => identity.unitKeywordList.includes(bv))
          }
          return false
        })
        if (!nameMatch && !keywordMatch && !traitMatch) return false
      }
      return true
    })
    // Sort: equipped first
    return filtered.sort((a, b) => {
      const aEquipped = equippedIdentityIds.has(a.id) ? 0 : 1
      const bEquipped = equippedIdentityIds.has(b.id) ? 0 : 1
      return aEquipped - bEquipped
    })
  }, [identities, selectedSinners, selectedKeywords, searchQuery, keywordToValue, traitToValue, equippedIdentityIds])

  const filteredAndSortedEgos = useMemo(() => {
    const filtered = egos.filter((ego) => {
      // Sinner filter
      if (selectedSinners.size > 0 && !selectedSinners.has(getSinnerFromId(ego.id))) {
        return false
      }
      // Keyword filter
      if (selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(selectedKeywords).every((kw) => ego.skillKeywordList.includes(kw))
        if (!hasAllKeywords) return false
      }
      // Search filter
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
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
  }, [egos, selectedSinners, selectedKeywords, searchQuery, keywordToValue, equippedEgoIds])

  // Construct deckState for StatusViewer
  const deckState: DeckState = useMemo(() => ({
    equipment,
    deploymentOrder,
    deploymentConfig: {
      maxDeployed: DEFAULT_DEPLOYMENT_MAX,
    },
  }), [equipment, deploymentOrder])

  // Handle deployment order toggle - only updates deploymentOrder state
  const handleToggleDeploy = useCallback((sinnerIndex: number) => {
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
  }, [])

  // Create lookup map for egos (needed for rank lookup in handleEquipEgo)
  const egoMap = useMemo(() => {
    const map: Record<string, EGO> = {}
    egos.forEach((e) => { map[e.id] = e })
    return map
  }, [egos])

  // Use ref to access egoMap in callback without dependency
  const egoMapRef = useRef(egoMap)
  useEffect(() => {
    egoMapRef.current = egoMap
  }, [egoMap])

  // Handle identity equip - only updates equipment state
  const handleEquipIdentity = useCallback(
    (identityId: string, data: { uptie?: UptieTier; level?: number }) => {
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
    },
    []
  )

  // Handle EGO equip - only updates equipment state
  const handleEquipEgo = useCallback((egoId: string, data: { threadspin?: ThreadspinTier }) => {
    const sinner = getSinnerFromId(egoId)
    const ego = egoMapRef.current[egoId]

    startTransition(() => {
      setEquipment((prev) => {
        const sinnerEquipment = prev[sinner]
        if (!sinnerEquipment || !ego) return prev

        const rank = ego.rank as EGORank
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
  }, [])

  // Handle reset deployment order
  const handleResetDeployment = () => {
    setDeploymentOrder([])
  }

  // Handle entity mode change
  const handleEntityModeChange = (mode: EntityMode) => {
    startTransition(() => setEntityMode(mode))
  }

  // Handle export deck code
  const handleExport = useCallback(async () => {
    try {
      const code = encodeDeckCode(equipment, deploymentOrder)
      await navigator.clipboard.writeText(code)
      toast.success(t('deckBuilder.exportSuccess'))
    } catch {
      toast.error(t('deckBuilder.exportError'))
    }
  }, [equipment, deploymentOrder, t])

  // Handle import deck code
  const handleImport = useCallback(async () => {
    if (!identitySpecMap || !egoSpecMap) return

    try {
      const clipboardText = await navigator.clipboard.readText()
      const validation = validateDeckCode(clipboardText)

      if (!validation.isValid) {
        toast.error(t('deckBuilder.importError'))
        return
      }

      const decoded = decodeDeckCode(clipboardText, identitySpecMap, egoSpecMap)
      setPendingImport(decoded)
      setImportDialogOpen(true)
    } catch {
      toast.error(t('deckBuilder.importError'))
    }
  }, [identitySpecMap, egoSpecMap, t])

  // Handle import confirmation
  const handleImportConfirm = useCallback(() => {
    if (!pendingImport) return

    startTransition(() => {
      setEquipment(pendingImport.equipment)
      setDeploymentOrder(pendingImport.deploymentOrder)
    })

    setImportDialogOpen(false)
    setPendingImport(null)
    toast.success(t('deckBuilder.importSuccess'))
  }, [pendingImport, t])

  // Handle import cancel
  const handleImportCancel = useCallback(() => {
    setImportDialogOpen(false)
    setPendingImport(null)
  }, [])

  if (identitiesPending || egosPending) {
    return <div className="p-4 text-muted-foreground">Loading...</div>
  }

  return (
    <div className="space-y-6">
      {/* Sinner Grid */}
      <div className="border rounded-lg p-4">
        <h3 className="text-sm font-semibold mb-3">Formation</h3>
        <SinnerGrid
          equipment={equipment}
          deploymentOrder={deploymentOrder}
          identities={identities}
          skillDataMap={skillDataMap}
          egoAffinityMap={egoAffinityMap}
          onToggleDeploy={handleToggleDeploy}
        />
        <div className="mt-3 flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={handleImport}>
            <Download className="w-4 h-4" />
            {t('deckBuilder.import')}
          </Button>
          <Button variant="outline" size="sm" onClick={handleExport}>
            <Upload className="w-4 h-4" />
            {t('deckBuilder.export')}
          </Button>
          <Button variant="outline" size="sm" onClick={handleResetDeployment}>
            Reset Order
          </Button>
        </div>
      </div>

      {/* Status Viewer */}
      <StatusViewer deckState={deckState} />

      {/* Entity Toggle and List */}
      <div className="border rounded-lg p-4 space-y-4">
        <div className="flex gap-4 justify-between flex-wrap">
          {/* Left side: Toggle and Filters */}
          <div className="flex gap-4 items-center flex-wrap">
            <EntityToggle mode={entityMode} onModeChange={handleEntityModeChange} />
            <SinnerFilter
              selectedSinners={selectedSinners}
              onSelectionChange={setSelectedSinners}
            />
            <KeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
          </div>
          {/* Right side: Search bar */}
          <div className="shrink-0">
            <SearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} placeholder="Search..." />
          </div>
        </div>

        {/* Both tabs rendered, hidden with CSS to preserve DOM and image cache */}
        <div className={entityMode === 'identity' ? '' : 'hidden'}>
          <div className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
            <div className="pt-4">
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
                {filteredAndSortedIdentities.map((identity) => {
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
              </div>
            </div>
          </div>
        </div>

        <div className={entityMode === 'ego' ? '' : 'hidden'}>
          <div className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
            <div className="pt-4">
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
                {filteredAndSortedEgos.map((ego) => {
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
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Import Confirmation Dialog */}
      <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('deckBuilder.importConfirmTitle')}</DialogTitle>
            <DialogDescription>
              {t('deckBuilder.importConfirmDescription')}
            </DialogDescription>
          </DialogHeader>

          {pendingImport && pendingImport.warnings.length > 0 && (
            <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md p-3">
              <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200 mb-2">
                {t('deckBuilder.importWarnings')}
              </p>
              <ul className="text-sm text-yellow-700 dark:text-yellow-300 list-disc list-inside">
                {pendingImport.warnings.map((warning, index) => (
                  <li key={index}>{warning}</li>
                ))}
              </ul>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={handleImportCancel}>
              {t('deckBuilder.cancel')}
            </Button>
            <Button onClick={handleImportConfirm}>
              {t('deckBuilder.apply')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default DeckBuilder
