import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { DeckBuilderContent } from './DeckBuilderContent'
import type { SinnerEquipment } from '@/types/DeckTypes'

interface DeckBuilderPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
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

/**
 * Dialog wrapper for the deck builder.
 * Contains only dialog chrome; all logic is in DeckBuilderContent.
 */
export function DeckBuilderPane({
  open,
  onOpenChange,
  onImport,
  onExport,
  onResetOrder,
  equipmentOverride,
  deploymentOrderOverride,
  setEquipmentOverride,
  setDeploymentOrderOverride,
  onIdentityChange,
}: DeckBuilderPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col" showCloseButton={false}>
          <DialogHeader className="shrink-0 border-b border-border pb-4">
            <div className="flex items-center justify-between">
              <DialogTitle>{t('deckBuilder.paneTitle')}</DialogTitle>
              <Button onClick={() => onOpenChange(false)} size="sm">
                {t('common:done')}
              </Button>
            </div>
          </DialogHeader>

          {/* Scrollable content area with visual margin */}
          <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6">
            <DeckBuilderContent
              mode="dialog"
              open={open}
              onImport={onImport}
              onExport={onExport}
              onResetOrder={onResetOrder}
              equipmentOverride={equipmentOverride}
              deploymentOrderOverride={deploymentOrderOverride}
              setEquipmentOverride={setEquipmentOverride}
              setDeploymentOrderOverride={setDeploymentOrderOverride}
              onIdentityChange={onIdentityChange}
            />
          </div>
        </DialogContent>
    </Dialog>
  )
}
