import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { LoadingState } from '@/components/feedback/LoadingState'

interface SelectorPaneShellProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Heading for the pane. */
  title: ReactNode
  /** Counters and per-pane controls, placed ahead of Done. */
  headerActions?: ReactNode
  children: ReactNode
}

/**
 * Dialog chrome for a full-width selection pane. Knows nothing about what is
 * being selected; the caller supplies the heading, its controls and the body.
 */
export function SelectorPaneShell({
  open,
  onOpenChange,
  title,
  headerActions,
  children,
}: SelectorPaneShellProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <div className="flex items-center gap-4 flex-wrap">
            <DialogTitle>{title}</DialogTitle>
            <div className="flex items-center gap-4 ml-auto">
              {headerActions}
              <Button
                size="sm"
                onClick={() => {
                  onOpenChange(false)
                }}
              >
                {t('common:done')}
              </Button>
            </div>
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
