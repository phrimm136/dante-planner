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

export interface ConflictItem {
  id: string
  localPlanner: SaveablePlanner
  serverPlanner: SaveablePlanner
}

export interface ConflictResolution {
  id: string
  choice: ConflictResolutionChoice
}

export interface BatchConflictDialogProps {
  open: boolean
  conflicts: ConflictItem[]
  onResolve: (resolutions: ConflictResolution[]) => void
  isResolving?: boolean
  outcomes?: ConflictOutcome[]
  onDismiss?: () => void
}

const CHOICE_ORDER: ConflictResolutionChoice[] = ['overwrite', 'discard', 'both']

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

export function BatchConflictDialog({
  open,
  conflicts,
  onResolve,
  isResolving = false,
  outcomes = [],
  onDismiss,
}: BatchConflictDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  const batchFailures = outcomes
    .map((outcome) => (outcome.result.ok ? null : outcome.result.error))
    .filter((failure): failure is ConflictFailure => failure?.step === 'precondition')

  const failureOf = (id: string): ConflictFailure | null => {
    const outcome = outcomes.find((entry) => entry.id === id)
    if (!outcome || outcome.result.ok) return null
    return outcome.result.error.step === 'precondition' ? null : outcome.result.error
  }

  const failureMessage = (failure: ConflictFailure): string => {
    const presentation = presentError(failure.error)
    if (!presentation) {
      return t('pages.plannerMD.batchConflict.itemFailed', 'This planner could not be resolved.')
    }
    return presentation.params ? t(presentation.key, presentation.params) : t(presentation.key)
  }

  const [resolutions, setResolutions] = useState<Record<string, ConflictResolutionChoice>>(() => {
    const initial: Record<string, ConflictResolutionChoice> = {}
    conflicts.forEach((conflict) => {
      initial[conflict.id] = 'overwrite'
    })
    return initial
  })

  const setResolution = (id: string, choice: ConflictResolutionChoice) => {
    setResolutions((prev) => ({ ...prev, [id]: choice }))
  }

  const applyToAll = (choice: ConflictResolutionChoice) => {
    const updated: Record<string, ConflictResolutionChoice> = {}
    conflicts.forEach((conflict) => {
      updated[conflict.id] = choice
    })
    setResolutions(updated)
  }

  const handleResolveAll = () => {
    const result: ConflictResolution[] = conflicts.map((conflict) => ({
      id: conflict.id,
      choice: resolutions[conflict.id] ?? 'overwrite',
    }))
    onResolve(result)
  }

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

        <div className="max-h-64 overflow-y-auto space-y-3 py-2">
          {conflicts.map((conflict) => {
            const currentChoice = resolutions[conflict.id] ?? 'overwrite'
            const failure = failureOf(conflict.id)
            return (
              <div
                key={conflict.id}
                className="flex flex-col gap-2 p-3 bg-muted rounded-md"
              >
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
                <p className={SECTION_STYLES.TEXT.captionSmall}>
                  {t('pages.plannerMD.batchConflict.localModified', 'Local')}: {formatDate(conflict.localPlanner.metadata.lastModifiedAt)}
                  {' | '}
                  {t('pages.plannerMD.batchConflict.serverModified', 'Server')}: {formatDate(conflict.serverPlanner.metadata.lastModifiedAt)}
                </p>
                {conflict.localPlanner.metadata.published && (
                  <p className={SECTION_STYLES.TEXT.captionSmall}>
                    {t('pages.plannerMD.conflict.keepBothUnpublished', 'The copy will not be published')}
                  </p>
                )}
                {failure && (
                  <p className="text-sm text-destructive" data-testid={`outcome-${conflict.id}`}>
                    {failureMessage(failure)}
                  </p>
                )}
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

function formatDate(isoString: string): string {
  return formatPlannerDate(isoString, undefined, DATE_FORMATS.SHORT_DATE_TIME) ?? MISSING_DATE_LABEL
}
