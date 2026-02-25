import { useState } from 'react'
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
import { cn } from '@/lib/utils'
import type { ConflictResolutionChoice, SaveablePlanner } from '@/types/PlannerTypes'

/**
 * A single conflict item with local and server planner data
 */
export interface ConflictItem {
  /** Unique planner ID */
  id: string
  /** Local version of the planner */
  localPlanner: SaveablePlanner
  /** Server version of the planner */
  serverPlanner: SaveablePlanner
}

/**
 * Resolution result for a single conflict
 */
export interface ConflictResolution {
  /** Planner ID */
  id: string
  /** Chosen resolution */
  choice: ConflictResolutionChoice
}

/**
 * Props for BatchConflictDialog
 */
export interface BatchConflictDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Array of conflicting planners (triggers at 2+) */
  conflicts: ConflictItem[]
  /** Callback when user resolves all conflicts */
  onResolve: (resolutions: ConflictResolution[]) => void
  /** Whether resolution is in progress */
  isResolving?: boolean
  /** Validation error from last resolution attempt (i18n key + optional params) */
  error?: { key: string; params?: Record<string, string> } | null
}

/**
 * Resolution choice button styling
 * - overwrite (Keep Local): destructive/red
 * - discard (Use Server): muted/neutral
 * - both (Save as Copy): same as discard for visual consistency
 */
const CHOICE_STYLES: Record<ConflictResolutionChoice, string> = {
  overwrite: 'bg-destructive/10 text-destructive border-destructive/30',
  discard: 'bg-muted text-muted-foreground border-border',
  both: 'bg-muted text-muted-foreground border-border',
}

const CHOICE_SELECTED_STYLES: Record<ConflictResolutionChoice, string> = {
  overwrite: 'bg-destructive text-destructive-foreground border-destructive',
  discard: 'bg-primary text-primary-foreground border-primary',
  both: 'bg-primary text-primary-foreground border-primary',
}

/**
 * Dialog for resolving multiple planner conflicts at once
 *
 * Triggers when 2+ conflicts are detected during sync.
 * Shows list of conflicting planners with per-item resolution buttons.
 * Provides "Apply to All" buttons for batch operations.
 *
 * @example
 * ```tsx
 * <BatchConflictDialog
 *   open={conflicts.length >= 2}
 *   conflicts={conflicts}
 *   onResolve={(resolutions) => {
 *     resolutions.forEach(r => handleResolution(r.id, r.choice))
 *   }}
 *   isResolving={isSaving}
 * />
 * ```
 */
export function BatchConflictDialog({
  open,
  conflicts,
  onResolve,
  isResolving = false,
  error = null,
}: BatchConflictDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Track resolution choice for each conflict
  const [resolutions, setResolutions] = useState<Record<string, ConflictResolutionChoice>>(() => {
    const initial: Record<string, ConflictResolutionChoice> = {}
    conflicts.forEach((conflict) => {
      initial[conflict.id] = 'overwrite' // Default to Keep Local
    })
    return initial
  })

  // Update resolution for a single conflict
  const setResolution = (id: string, choice: ConflictResolutionChoice) => {
    setResolutions((prev) => ({ ...prev, [id]: choice }))
  }

  // Apply same resolution to all conflicts
  const applyToAll = (choice: ConflictResolutionChoice) => {
    const updated: Record<string, ConflictResolutionChoice> = {}
    conflicts.forEach((conflict) => {
      updated[conflict.id] = choice
    })
    setResolutions(updated)
  }

  // Submit all resolutions
  const handleResolveAll = () => {
    const result: ConflictResolution[] = conflicts.map((conflict) => ({
      id: conflict.id,
      choice: resolutions[conflict.id] ?? 'overwrite',
    }))
    onResolve(result)
  }

  // Prevent dismissal via ESC key or clicking outside
  const preventDismissal = (e: Event) => {
    e.preventDefault()
  }

  // Resolution choice labels
  const choiceLabels: Record<ConflictResolutionChoice, string> = {
    overwrite: t('pages.plannerMD.conflict.overwrite', 'Keep Local'),
    discard: t('pages.plannerMD.conflict.discard', 'Use Server'),
    both: t('pages.plannerMD.conflict.keepBoth', 'Keep Both'),
  }

  // Choice button component
  const ChoiceButton = ({
    choice,
    selected,
    onClick,
  }: {
    choice: ConflictResolutionChoice
    selected: boolean
    onClick: () => void
  }) => (
    <button
      type="button"
      onClick={onClick}
      disabled={isResolving}
      className={cn(
        'px-2 py-1 text-xs rounded border transition-colors',
        selected ? CHOICE_SELECTED_STYLES[choice] : CHOICE_STYLES[choice],
        isResolving && 'opacity-50 cursor-not-allowed'
      )}
    >
      {choiceLabels[choice]}
    </button>
  )

  return (
    <Dialog open={open}>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={preventDismissal}
        onInteractOutside={preventDismissal}
        className="max-w-2xl"
      >
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.batchConflict.title', 'Conflicts Detected')}
          </DialogTitle>
          <DialogDescription>
            {t(
              'pages.plannerMD.batchConflict.description',
              '{{count}} planners have conflicts. Choose how to resolve each one.',
              { count: conflicts.length }
            )}
          </DialogDescription>
        </DialogHeader>

        {/* Apply to All section - vertical layout */}
        <div className="flex flex-col gap-2 py-3 border-b border-border">
          <span className="text-sm text-muted-foreground">
            {t('pages.plannerMD.batchConflict.applyToAll', 'Apply to all')}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => applyToAll('overwrite')}
              disabled={isResolving}
              className={cn(
                'px-3 py-1.5 text-sm rounded border transition-colors',
                CHOICE_STYLES.overwrite,
                isResolving && 'opacity-50 cursor-not-allowed'
              )}
            >
              {choiceLabels.overwrite}
            </button>
            <button
              type="button"
              onClick={() => applyToAll('discard')}
              disabled={isResolving}
              className={cn(
                'px-3 py-1.5 text-sm rounded border transition-colors',
                CHOICE_STYLES.discard,
                isResolving && 'opacity-50 cursor-not-allowed'
              )}
            >
              {choiceLabels.discard}
            </button>
            <button
              type="button"
              onClick={() => applyToAll('both')}
              disabled={isResolving}
              className={cn(
                'px-3 py-1.5 text-sm rounded border transition-colors',
                CHOICE_STYLES.both,
                isResolving && 'opacity-50 cursor-not-allowed'
              )}
            >
              {choiceLabels.both}
            </button>
          </div>
        </div>

        {/* Conflict list - scrollable */}
        <div className="max-h-64 overflow-y-auto space-y-3 py-2">
          {conflicts.map((conflict) => {
            const currentChoice = resolutions[conflict.id] ?? 'overwrite'
            return (
              <div
                key={conflict.id}
                className="flex flex-col gap-2 p-3 bg-muted rounded-md"
              >
                {/* Title + Published indicator */}
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-medium truncate min-w-0">
                    {conflict.localPlanner.metadata.title}
                  </p>
                  {conflict.serverPlanner.metadata.published && (
                    <span className="shrink-0 px-1.5 py-0.5 text-[10px] font-medium rounded bg-primary/10 text-primary border border-primary/30">
                      {t('pages.plannerMD.batchConflict.published', 'Published')}
                    </span>
                  )}
                </div>
                {/* Save dates */}
                <p className="text-xs text-muted-foreground">
                  {t('pages.plannerMD.batchConflict.localModified', 'Local')}: {formatDate(conflict.localPlanner.metadata.lastModifiedAt)}
                  {' | '}
                  {t('pages.plannerMD.batchConflict.serverModified', 'Server')}: {formatDate(conflict.serverPlanner.metadata.lastModifiedAt)}
                </p>
                {/* Buttons */}
                <div className="flex gap-1">
                  <ChoiceButton
                    choice="overwrite"
                    selected={currentChoice === 'overwrite'}
                    onClick={() => setResolution(conflict.id, 'overwrite')}
                  />
                  <ChoiceButton
                    choice="discard"
                    selected={currentChoice === 'discard'}
                    onClick={() => setResolution(conflict.id, 'discard')}
                  />
                  <ChoiceButton
                    choice="both"
                    selected={currentChoice === 'both'}
                    onClick={() => setResolution(conflict.id, 'both')}
                  />
                </div>
              </div>
            )
          })}
        </div>

        <DialogFooter className="flex-col items-stretch gap-2 sm:flex-col">
          {error && (
            <p className="text-sm text-destructive">
              {t(error.key, { ns: 'planner', ...error.params })}
            </p>
          )}
          <Button onClick={handleResolveAll} disabled={isResolving}>
            {t('pages.plannerMD.batchConflict.resolveAll', 'Resolve All')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Format ISO date string for display
 */
function formatDate(isoString: string): string {
  try {
    const date = new Date(isoString)
    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    })
  } catch {
    return '-'
  }
}

export default BatchConflictDialog
