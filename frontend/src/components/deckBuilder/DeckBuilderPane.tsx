import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { DeckBuilderContent } from './DeckBuilderContent'

interface DeckBuilderPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
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
}: DeckBuilderPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col">
          <DialogHeader className="shrink-0 border-b border-border pb-4">
            <DialogTitle>{t('deckBuilder.paneTitle')}</DialogTitle>
          </DialogHeader>

          {/* Scrollable content area with visual margin */}
          <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6">
            <DeckBuilderContent
              mode="dialog"
              open={open}
              onImport={onImport}
              onExport={onExport}
              onResetOrder={onResetOrder}
            />
          </div>

          <DialogFooter className="shrink-0 border-t border-border pt-4">
            <Button onClick={() => onOpenChange(false)}>
              {t('common:done')}
            </Button>
          </DialogFooter>
        </DialogContent>
    </Dialog>
  )
}
