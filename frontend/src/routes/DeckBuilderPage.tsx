// React core
import { Suspense, useId, useState } from 'react'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

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
import { CARD_GRID } from '@/lib/constants'

// Project utilities (@/lib)
import { encodeDeckCode, decodeDeckCode, validateDeckCode } from '@/lib/deckCode'

// Project types & schemas
import type { DecodedDeck } from '@/lib/deckCode'

// Store
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from '@/stores/usePlannerEditorStore'

// Project hooks
import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import { useEGOListSpec } from '@/hooks/useEGOListData'

// Project components (@/components)
import { DeckBuilderContent } from '@/components/deckBuilder/DeckBuilderContent'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'

/** Sinner mini-card dimensions (matches SinnerGrid portrait) */
const SINNER_CARD = { width: 64, height: 80 }

/**
 * Page-level skeleton for deck builder initial load
 * Matches actual DeckBuilderContent structure for seamless transition
 */
function DeckBuilderPageSkeleton() {
  return (
    <div className="container mx-auto p-8">
      <div className="space-y-6">
        {/* Sinner grid placeholder */}
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
        </div>

        {/* Status and action bar placeholder */}
        <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
          <Skeleton className="h-10 w-48" />
          <div className="flex gap-2">
            <Skeleton className="h-10 w-24" />
            <Skeleton className="h-10 w-24" />
            <Skeleton className="h-10 w-24" />
          </div>
        </div>

        {/* Card grid section placeholder */}
        <div className="border-2 border-border rounded-lg p-6 space-y-4">
          {/* Toggle and filters */}
          <div className="flex gap-4 justify-between flex-wrap">
            <div className="flex gap-4 items-center flex-wrap">
              <Skeleton className="h-10 w-32" />
              <Skeleton className="h-10 w-32" />
              <Skeleton className="h-10 w-32" />
            </div>
            <Skeleton className="h-10 w-48" />
          </div>

          {/* Card grid with actual card dimensions */}
          <div className="bg-muted border border-border rounded-md p-6 max-h-[600px]">
            <div className="pt-4">
              <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
                {Array.from({ length: 10 }).map((_, i) => (
                  <Skeleton
                    key={i}
                    style={{
                      width: CARD_GRID.WIDTH.IDENTITY,
                      height: 224,
                      clipPath: 'polygon(4% 0%, 96% 0%, 100% 4%, 100% 96%, 96% 100%, 4% 100%, 0% 96%, 0% 4%)',
                    }}
                  />
                ))}
              </ResponsiveCardGrid>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * Inner content component with store access and handlers
 */
function DeckBuilderPageContent() {
  const { t } = useTranslation(['planner', 'common'])
  const storeApi = usePlannerEditorStoreApi()

  // Store actions
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)

  // Spec data for deck code decoding
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  // Import dialog state
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  // Handlers (pattern from PlannerMDEditorContent)
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

  return (
    <div className="container mx-auto p-8">
      <DeckBuilderContent
        onImport={handleImport}
        onExport={handleExport}
        onResetOrder={handleResetOrder}
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
 * State resets on navigation (fresh store per mount).
 * Uses useId() as key to guarantee store recreation on remount.
 */
export default function DeckBuilderPage() {
  // useId generates unique ID per component instance
  // Forces fresh store when component remounts after navigation
  const storeKey = useId()

  return (
    <PlannerEditorStoreProvider key={storeKey}>
      <Suspense fallback={<DeckBuilderPageSkeleton />}>
        <DeckBuilderPageContent />
      </Suspense>
    </PlannerEditorStoreProvider>
  )
}
