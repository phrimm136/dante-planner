// React core
import { Suspense, useId, useState } from 'react'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'

// shadcn/ui components
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'

// Project constants
import { DEFAULT_SKILL_EA } from '@/lib/constants'

// Project utilities (@/lib)
import { encodeDeckCode, decodeDeckCode, validateDeckCode } from './lib/deckCode'

// Project types & schemas
import type { DecodedDeck } from './lib/deckCode'

// Store
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from './stores/usePlannerEditorStore'

// Project hooks
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'

// Project components (@/components)
import { DeckBuilderSummary } from './components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from './components/deckBuilder/DeckBuilderPane'

/** Sinner card dimensions for skeleton (matches SinnerGrid) */
const SINNER_CARD = { width: 96, height: 128 }

/**
 * Page-level skeleton matching DeckBuilderSummary structure
 */
function DeckBuilderPageSkeleton() {
  return (
    <div className="container mx-auto p-8">
      <div className="space-y-4">
        {/* Section header */}
        <Skeleton className="h-8 w-40" />

        {/* SinnerGrid placeholder */}
        <div className="border-2 border-border rounded-lg p-4">
          <div className="flex flex-wrap gap-2">
            {Array.from({ length: 12 }).map((_, i) => (
              <Skeleton
                key={i}
                className="rounded-md"
                style={{
                  width: SINNER_CARD.width,
                  height: SINNER_CARD.height,
                  animationDelay: `${i * 40}ms`,
                }}
              />
            ))}
          </div>

          {/* Status + Action bar placeholder */}
          <div className="mt-3 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
            <Skeleton className="h-20 w-full lg:w-96" />
            <div className="flex gap-2">
              <Skeleton className="h-10 w-24" />
              <Skeleton className="h-10 w-24" />
              <Skeleton className="h-10 w-24" />
              <Skeleton className="h-10 w-24" />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * Inner content component with store access and handlers.
 * Uses Summary + Pane pattern: SinnerGrid viewer + Edit dialog.
 */
function DeckBuilderPageContent() {
  const { t } = useTranslation(['planner', 'common'])
  const storeApi = usePlannerEditorStoreApi()

  // Store actions
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)

  // Spec data for deck code decoding
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  // Pane (edit dialog) state
  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)

  // Import dialog state
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  // Handlers
  const handleExport = async () => {
    try {
      const { equipment, deploymentOrder } = storeApi.getState()
      const code = encodeDeckCode(equipment, deploymentOrder)
      await navigator.clipboard.writeText(code)
      toast.success(t('deckBuilder.exportSuccess'))
    } catch (error) {
      console.error('[DeckBuilder] Export failed:', error)
      toast.error(t('deckBuilder.exportError'))
    }
  }

  const handleImport = async () => {
    try {
      const clipboardText = await navigator.clipboard.readText()
      const validation = validateDeckCode(clipboardText)

      if (!validation.isValid) {
        console.error('[DeckBuilder] Invalid deck code:', validation)
        toast.error(t('deckBuilder.importError'))
        return
      }

      const decoded = decodeDeckCode(clipboardText, identitySpec, egoSpec)
      setPendingImport(decoded)
      setImportDialogOpen(true)
    } catch (error) {
      console.error('[DeckBuilder] Import failed:', error)
      toast.error(t('deckBuilder.importError'))
    }
  }

  const handleImportConfirm = () => {
    if (!pendingImport) return

    setEquipment(pendingImport.equipment)
    setDeploymentOrder(pendingImport.deploymentOrder)

    setImportDialogOpen(false)
    setPendingImport(null)
    toast.success(t('deckBuilder.importSuccess'))
  }

  const handleImportCancel = () => {
    setImportDialogOpen(false)
    setPendingImport(null)
  }

  const handleResetOrder = () => {
    setDeploymentOrder([])
  }

  const handleToggleDeploy = (sinnerIndex: number) => {
    const currentIndex = deploymentOrder.indexOf(sinnerIndex)
    if (currentIndex >= 0) {
      const newOrder = [...deploymentOrder]
      newOrder.splice(currentIndex, 1)
      setDeploymentOrder(newOrder)
    } else {
      setDeploymentOrder([...deploymentOrder, sinnerIndex])
    }
  }

  return (
    <div className="container mx-auto p-8">
      {/* Summary view: SinnerGrid + StatusViewer + ActionBar */}
      <DeckBuilderSummary
        onToggleDeploy={handleToggleDeploy}
        onImport={handleImport}
        onExport={handleExport}
        onResetOrder={handleResetOrder}
        onEditDeck={() => setIsDeckPaneOpen(true)}
      />

      {/* Edit dialog: full card selection grid */}
      <DeckBuilderPane
        open={isDeckPaneOpen}
        onOpenChange={setIsDeckPaneOpen}
        onImport={handleImport}
        onExport={handleExport}
        onResetOrder={handleResetOrder}
        onIdentityChange={(sinnerCode) => {
          updateSinnerSkillEA(sinnerCode, { ...DEFAULT_SKILL_EA })
        }}
      />

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

/**
 * Standalone deck builder page with ephemeral state.
 * Uses Summary + Pane pattern: rich SinnerGrid viewer with Edit dialog.
 * State resets on navigation (fresh store per mount).
 */
export default function DeckBuilderPage() {
  const storeKey = useId()

  return (
    <PlannerEditorStoreProvider key={storeKey}>
      <Suspense fallback={<DeckBuilderPageSkeleton />}>
        <DeckBuilderPageContent />
      </Suspense>
    </PlannerEditorStoreProvider>
  )
}
