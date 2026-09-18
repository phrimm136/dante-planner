import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { DATE_FORMATS, formatPlannerDate } from '@/lib/formatDate'
import { presentError } from '@/lib/errorPresentation'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { ConflictState, ConflictResolutionChoice } from '../../types/PlannerTypes'
import { SECTION_STYLES } from '@/lib/constants'

export interface ConflictResolutionDialogProps {
  open: boolean
  conflictState: ConflictState | null
  onChoice: (choice: ConflictResolutionChoice) => void
  isResolving?: boolean
  resolutionError?: AppError | null
}

export function ConflictResolutionDialog({
  open,
  conflictState,
  onChoice,
  isResolving = false,
  resolutionError = null,
}: ConflictResolutionDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  const formatTime = (isoString: string): string =>
    formatPlannerDate(isoString, undefined, DATE_FORMATS.TIME_ONLY) ?? ''

  const failure = resolutionError && presentError(resolutionError)
  const failureMessage = !resolutionError
    ? null
    : failure
      ? failure.params
        ? t(failure.key, failure.params)
        : t(failure.key)
      : t(
          'pages.plannerMD.conflict.conflictAgain',
          'The planner changed again while this conflict was open. Choose again.',
        )

  const preventDismissal = (e: Event) => {
    e.preventDefault()
  }

  return (
    <Dialog open={open}>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={preventDismissal}
        onInteractOutside={preventDismissal}
      >
        <DialogHeader>
          <DialogTitle>{t('pages.plannerMD.conflict.title', 'Save Conflict Detected')}</DialogTitle>
          <DialogDescription>
            {t(
              'pages.plannerMD.conflict.description',
              'This planner was modified on another device or tab. Your local changes conflict with the server version.',
            )}
          </DialogDescription>
        </DialogHeader>

        {conflictState && (
          <div className="py-2 text-sm text-muted-foreground">
            {t('pages.plannerMD.conflict.info', {
              time: formatTime(conflictState.detectedAt),
              defaultValue: `Conflict detected at ${formatTime(conflictState.detectedAt)}`,
            })}
          </div>
        )}

        <p className={SECTION_STYLES.TEXT.captionSmall}>
          {t('pages.plannerMD.conflict.keepBothUnpublished', 'The copy will not be published')}
        </p>

        {failureMessage && <p className="text-sm text-destructive">{failureMessage}</p>}

        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button
            variant="outline"
            onClick={() => {
              onChoice('discard')
            }}
            disabled={isResolving}
          >
            {t('pages.plannerMD.conflict.discard', 'Use Server')}
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              onChoice('both')
            }}
            disabled={isResolving}
          >
            {t('pages.plannerMD.conflict.keepBoth', 'Keep Both')}
          </Button>
          <Button
            variant="destructive"
            onClick={() => {
              onChoice('overwrite')
            }}
            disabled={isResolving}
          >
            {t('pages.plannerMD.conflict.overwrite', 'Keep Local')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
