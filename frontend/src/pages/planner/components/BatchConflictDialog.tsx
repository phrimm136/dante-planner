import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cva } from 'class-variance-authority'
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
import { DATE_FORMATS, formatPlannerDate } from '@/lib/formatDate'
import { presentError } from '@/lib/errorPresentation'
import type { ConflictFailure, ConflictOutcome } from '../lib/conflictChoice'
import type { ConflictResolutionChoice, SaveablePlanner } from '../types/PlannerTypes'
import { SECTION_STYLES } from '@/lib/constants'

const MISSING_DATE_LABEL = '-'

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
  /** One entry per attempted item, in submission order. */
  outcomes?: ConflictOutcome[]
  /**
   * The user closed the dialog. Its consumer decides what that means — parking
   * the batch behind a reopen, or cancelling the run that raised it.
   */
  onDismiss?: () => void
}

/** The choices in the order both the per-item row and the apply-to-all row show them. */
const CHOICE_ORDER: ConflictResolutionChoice[] = ['overwrite', 'discard', 'both']

/**
 * Resolution choice button styling
 * - overwrite (Keep Local): destructive/red
 * - discard (Use Server): muted/neutral
 * - both (Save as Copy): same as discard for visual consistency
 */
const choiceButtonVariants = cva(
  'rounded border transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
  {
    variants: {
      choice: { overwrite: '', discard: '', both: '' },
      selected: { true: '', false: '' },
      size: { sm: 'px-2 py-1 text-xs', md: 'px-3 py-1.5 text-sm' },
    },
    compoundVariants: [
      {
        choice: 'overwrite',
        selected: false,
        class: 'bg-destructive/10 text-destructive border-destructive/30',
      },
      {
        choice: 'overwrite',
        selected: true,
        class: 'bg-destructive text-destructive-foreground border-destructive',
      },
      { choice: 'discard', selected: false, class: 'bg-muted text-muted-foreground border-border' },
      {
        choice: 'discard',
        selected: true,
        class: 'bg-primary text-primary-foreground border-primary',
      },
      { choice: 'both', selected: false, class: 'bg-muted text-muted-foreground border-border' },
      { choice: 'both', selected: true, class: 'bg-primary text-primary-foreground border-primary' },
    ],
    defaultVariants: { selected: false, size: 'sm' },
  }
)

function ChoiceButton({
  choice,
  label,
  selected = false,
  size,
  disabled,
  onClick,
}: {
  choice: ConflictResolutionChoice
  label: string
  selected?: boolean
  size?: 'sm' | 'md'
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(choiceButtonVariants({ choice, selected, size }))}
    >
      {label}
    </button>
  )
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
  outcomes = [],
  onDismiss,
}: BatchConflictDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  /** Failures of the whole submission, which belong to no single row. */
  const batchFailures = outcomes
    .map((outcome) => (outcome.result.ok ? null : outcome.result.error))
    .filter((failure): failure is ConflictFailure => failure?.step === 'precondition')

  /** Why the attempt on this row failed, or null when it did not fail. */
  const failureOf = (id: string): ConflictFailure | null => {
    const outcome = outcomes.find((entry) => entry.id === id)
    if (!outcome || outcome.result.ok) return null
    // A precondition failure stopped the submission before this row was reached.
    return outcome.result.error.step === 'precondition' ? null : outcome.result.error
  }

  const failureMessage = (failure: ConflictFailure): string => {
    const presentation = presentError(failure.error)
    if (!presentation) {
      // A conflict has no message of its own; this dialog is what reports it.
      return t('pages.plannerMD.batchConflict.itemFailed', 'This planner could not be resolved.')
    }
    return presentation.params ? t(presentation.key, presentation.params) : t(presentation.key)
  }

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

  // Resolution choice labels
  const choiceLabels: Record<ConflictResolutionChoice, string> = {
    overwrite: t('pages.plannerMD.conflict.overwrite', 'Keep Local'),
    discard: t('pages.plannerMD.conflict.discard', 'Use Server'),
    both: t('pages.plannerMD.conflict.keepBoth', 'Keep Both'),
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onDismiss?.()
      }}
    >
      <DialogContent className="max-w-2xl">
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

        {batchFailures.map((failure, index) => (
          <p key={index} className="text-sm text-destructive" data-testid="batch-failure">
            {failureMessage(failure)}
          </p>
        ))}

        {/* Apply to All section - vertical layout */}
        <div className="flex flex-col gap-2 py-3 border-b border-border">
          <span className={SECTION_STYLES.TEXT.caption}>
            {t('pages.plannerMD.batchConflict.applyToAll', 'Apply to all')}
          </span>
          <div className="flex gap-2">
            {CHOICE_ORDER.map((choice) => (
              <ChoiceButton
                key={choice}
                choice={choice}
                label={choiceLabels[choice]}
                size="md"
                disabled={isResolving}
                onClick={() => applyToAll(choice)}
              />
            ))}
          </div>
        </div>

        {/* Conflict list - scrollable */}
        <div className="max-h-64 overflow-y-auto space-y-3 py-2">
          {conflicts.map((conflict) => {
            const currentChoice = resolutions[conflict.id] ?? 'overwrite'
            const failure = failureOf(conflict.id)
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
                <p className={SECTION_STYLES.TEXT.captionSmall}>
                  {t('pages.plannerMD.batchConflict.localModified', 'Local')}: {formatDate(conflict.localPlanner.metadata.lastModifiedAt)}
                  {' | '}
                  {t('pages.plannerMD.batchConflict.serverModified', 'Server')}: {formatDate(conflict.serverPlanner.metadata.lastModifiedAt)}
                </p>
                {/* Notification that copy won't be published */}
                {conflict.localPlanner.metadata.published && (
                  <p className={SECTION_STYLES.TEXT.captionSmall}>
                    {t('pages.plannerMD.conflict.keepBothUnpublished', 'The copy will not be published')}
                  </p>
                )}
                {/* Why the attempt on this row failed */}
                {failure && (
                  <p className="text-sm text-destructive" data-testid={`outcome-${conflict.id}`}>
                    {failureMessage(failure)}
                  </p>
                )}
                {/* Buttons */}
                <div className="flex gap-1">
                  {CHOICE_ORDER.map((choice) => (
                    <ChoiceButton
                      key={choice}
                      choice={choice}
                      label={choiceLabels[choice]}
                      selected={currentChoice === choice}
                      disabled={isResolving}
                      onClick={() => setResolution(conflict.id, choice)}
                    />
                  ))}
                </div>
              </div>
            )
          })}
        </div>

        <DialogFooter className="flex-col items-stretch gap-2 sm:flex-col">
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
  return formatPlannerDate(isoString, undefined, DATE_FORMATS.SHORT_DATE_TIME) ?? MISSING_DATE_LABEL
}
