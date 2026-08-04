import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { LoadingState } from '@/components/feedback/LoadingState'
import { SECTION_STYLES } from '@/lib/constants'

interface DeckBuilderPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  children: ReactNode
}

/**
 * Dialog chrome for the deck builder. Knows nothing about the deck it hosts;
 * the caller supplies whichever builder its surface owns.
 */
export function DeckBuilderPane({ open, onOpenChange, children }: DeckBuilderPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <div className={SECTION_STYLES.LAYOUT.rowBetween}>
            <DialogTitle>{t('deckBuilder.paneTitle')}</DialogTitle>
            <Button onClick={() => onOpenChange(false)} size="sm">
              {t('common:done')}
            </Button>
          </div>
        </DialogHeader>

        {/* Scrollable content area with visual margin */}
        <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6">
          <Suspense fallback={<LoadingState />}>{children}</Suspense>
        </div>
      </DialogContent>
    </Dialog>
  )
}
