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
import type { ConflictState, ConflictResolutionChoice, SaveablePlanner } from '@/types/PlannerTypes'

/**
 * Props for ConflictResolutionDialog
 */
export interface ConflictResolutionDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Conflict information (serverVersion, detectedAt) */
  conflictState: ConflictState | null
  /** Local planner data (for display in enhanced mode) */
  localPlanner?: SaveablePlanner
  /** Server planner data (for display in enhanced mode) */
  serverPlanner?: SaveablePlanner
  /** Callback when user makes a choice */
  onChoice: (choice: ConflictResolutionChoice) => void
  /** Whether resolution is in progress */
  isResolving?: boolean
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
 *   localPlanner={localPlanner}
 *   serverPlanner={serverPlanner}
 *   onChoice={resolveConflict}
 *   isResolving={isSaving}
 * />
 * ```
 */
export function ConflictResolutionDialog({
  open,
  conflictState,
  localPlanner,
  serverPlanner,
  onChoice,
  isResolving = false,
}: ConflictResolutionDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Format the conflict detection time for display
  const formatTime = (isoString: string): string => {
    try {
      const date = new Date(isoString)
      return date.toLocaleTimeString()
    } catch {
      return ''
    }
  }

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
          <DialogTitle>
            {t('pages.plannerMD.conflict.title', 'Save Conflict Detected')}
          </DialogTitle>
          <DialogDescription>
            {t(
              'pages.plannerMD.conflict.description',
              'This planner was modified on another device or tab. Your local changes conflict with the server version.'
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

        {/* Version comparison when planner data is available */}
        {localPlanner && serverPlanner && (
          <div className="space-y-2 py-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">
                {t('pages.plannerMD.conflict.localVersion', 'Local version')}:
              </span>
              <span className="font-medium truncate max-w-[60%]">
                {localPlanner.metadata.title}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">
                {t('pages.plannerMD.conflict.serverVersion', 'Server version')}:
              </span>
              <span className="font-medium truncate max-w-[60%]">
                {serverPlanner.metadata.title}
              </span>
            </div>
          </div>
        )}

        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button
            variant="outline"
            onClick={() => { onChoice('discard'); }}
            disabled={isResolving}
          >
            {t('pages.plannerMD.conflict.discard', 'Use Server')}
          </Button>
          <Button
            variant="secondary"
            onClick={() => { onChoice('both'); }}
            disabled={isResolving}
          >
            {t('pages.plannerMD.conflict.keepBoth', 'Keep Both')}
          </Button>
          <Button
            variant="destructive"
            onClick={() => { onChoice('overwrite'); }}
            disabled={isResolving}
          >
            {t('pages.plannerMD.conflict.overwrite', 'Keep Local')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default ConflictResolutionDialog
