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

/**
 * Props for ConflictResolutionDialog
 */
export interface ConflictResolutionDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Conflict information (serverVersion, detectedAt) */
  conflictState: ConflictState | null
  /** Callback when user makes a choice */
  onChoice: (choice: ConflictResolutionChoice) => void
  /** Whether resolution is in progress */
  isResolving?: boolean
  /** Why the last attempt at this conflict failed, or null when none has */
  resolutionError?: AppError | null
}

/**
 * Dialog for resolving save conflicts (409 errors)
 *
 * Shown when the server version has changed since the user started editing.
 * Offers three choices:
 * - Keep Local (overwrite): Force-save local changes, discarding server changes
 * - Use Server (discard): Reload server version, losing local changes
 * - Keep Both: Create copy of server version with new UUID and "(Copy)" suffix
 *
 * @example
 * ```tsx
 * <ConflictResolutionDialog
 *   open={errorCode === 'conflict'}
 *   conflictState={conflictState}
 *   onChoice={resolveConflict}
 *   isResolving={isSaving}
 * />
 * ```
 */
export function ConflictResolutionDialog({
  open,
  conflictState,
  onChoice,
  isResolving = false,
  resolutionError = null,
}: ConflictResolutionDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Format the conflict detection time for display
  const formatTime = (isoString: string): string =>
    formatPlannerDate(isoString, undefined, DATE_FORMATS.TIME_ONLY) ?? ''

  // This dialog is the mounted owner of the conflict, so a failure it caused is
  // reported here rather than through a toast the conflict itself never gets.
  const failure = resolutionError && presentError(resolutionError)
  const failureMessage = !resolutionError
    ? null
    : failure
      ? t(failure.key, failure.params)
      : // The presenter yields nothing for a conflict, and this dialog is what owns it.
        t(
          'pages.plannerMD.conflict.conflictAgain',
          'The planner changed again while this conflict was open. Choose again.',
        )

  // Prevent dismissal via ESC key or clicking outside
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

        {conflictState?.serverVersion != null && (
          <div className="flex justify-between py-2 text-sm">
            <span className={SECTION_STYLES.TEXT.muted}>
              {t('pages.plannerMD.conflict.serverVersion', 'Server version')}:
            </span>
            <span className="font-medium">{conflictState.serverVersion}</span>
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
