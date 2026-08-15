import { Suspense, useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import {
  showError,
  showErrorMessage,
  showInfo,
  showSuccess,
  showWarning,
} from '@/lib/errorPresentation'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { BatchConflictDialog } from './BatchConflictDialog'
import { usePlannerStorage } from '../hooks/usePlannerStorage'
import { EXPORT_FILE_EXTENSION, EXPORT_MAX_FILE_SIZE, SECTION_STYLES } from '@/lib/constants'
import { downloadBlob } from '@/lib/downloadBlob'
import { generateUUID } from '@/lib/uuid'
import { assertNever } from '@/lib/utils'
import { ok, err } from '@/lib/result'
import {
  IMPORT_OUTCOME_TOASTS,
  RESOLVE_OUTCOME_TOASTS,
  buildExportEnvelope,
  classifyImportOutcome,
  classifyResolveOutcome,
  decompressImport,
  encodeExportEnvelope,
  exportFileName,
  getValidDeviceId,
  importErrorToast,
  parseImportJson,
  partitionImport,
  readGzipBytes,
  readImportEnvelope,
  sanitizePlannerTitle,
  toExportItem,
} from '../lib/plannerExportImport'
import { planConflictResolution } from '../lib/conflictChoice'

import type { Result } from '@/lib/result'
import type { ConflictItem, ConflictResolution } from './BatchConflictDialog'
import type { ConflictResolutionContext } from '../lib/conflictChoice'
import type { PlannerExportItem, SaveablePlanner } from '../types/PlannerTypes'
import type { ImportError, ResolveCounts, ToastDescriptor } from '../lib/plannerExportImport'

const MIME_TYPE = 'application/gzip'

/** Stable empty list so a closed dialog keeps a single conflicts identity. */
const NO_CONFLICTS: ConflictItem[] = []

/**
 * Run a phase, answering with what it produced or with what it threw.
 *
 * The React Compiler declines to compile a component holding a `try` block, so
 * the storage and file APIs that throw are caught out here instead.
 */
async function attempt<T>(run: () => Promise<T>): Promise<Result<T, unknown>> {
  try {
    return ok(await run())
  } catch (error) {
    return err(error)
  }
}

/**
 * What the section is doing. `awaitingChoice` and `resolving` hold the progress
 * the import reached, which stays on screen while the dialog is up.
 */
type SectionState =
  | { k: 'idle' }
  | { k: 'exporting'; pct: number }
  | { k: 'importing'; pct: number }
  | { k: 'awaitingChoice'; pct: number; conflicts: ConflictItem[] }
  | { k: 'resolving'; pct: number; conflicts: ConflictItem[] }

/**
 * Inner component that contains the export/import logic.
 * Must be wrapped in Suspense boundary.
 */
function PlannerExportImportSectionContent() {
  const { t } = useTranslation(['common', 'planner'])
  const { listLocal, loadFromLocal, saveToLocal, getOrCreateDeviceId } = usePlannerStorage()

  const [state, setState] = useState<SectionState>({ k: 'idle' })

  // File input ref
  const fileInputRef = useRef<HTMLInputElement>(null)

  const isProcessing = state.k !== 'idle'
  const progress = state.k === 'idle' ? 0 : state.pct
  const conflicts =
    state.k === 'awaitingChoice' || state.k === 'resolving' ? state.conflicts : NO_CONFLICTS

  const showToast = (descriptor: ToastDescriptor, params?: Record<string, number>) => {
    const key = `common:${descriptor.key}`
    switch (descriptor.severity) {
      case 'success':
        showSuccess(key, params)
        return
      case 'warning':
        showWarning(key, params)
        return
      case 'info':
        showInfo(key, params)
        return
      case 'error':
        showErrorMessage(key, params)
        return
      default:
        assertNever(descriptor.severity)
    }
  }

  /**
   * Release the file input so the same file can be picked again
   */
  const clearFileInput = () => {
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  /**
   * Collect every stored planner, encode the file, and hand it to the browser
   */
  const writeExportFile = async () => {
    // Get all planner summaries
    const summaries = await listLocal()

    if (summaries.length === 0) {
      showInfo('common:exportImport.noPlannersToExport')
      return
    }

    // Load full planner data in parallel batches
    const BATCH_SIZE = 10
    const planners: PlannerExportItem[] = []

    for (let i = 0; i < summaries.length; i += BATCH_SIZE) {
      const batch = summaries.slice(i, i + BATCH_SIZE)
      const results = await Promise.all(batch.map((s) => loadFromLocal(s.id)))

      for (const loaded of results) {
        if (loaded.ok && loaded.value) {
          planners.push(toExportItem(loaded.value))
        }
      }
      setState({
        k: 'exporting',
        pct: Math.round(((i + batch.length) / summaries.length) * 50),
      })
    }

    if (planners.length === 0) {
      showErrorMessage('common:exportImport.exportFailed')
      return
    }

    const deviceId = await getOrCreateDeviceId()
    if (!deviceId.ok) {
      showErrorMessage('common:exportImport.exportFailed')
      return
    }

    const envelope = buildExportEnvelope(planners, deviceId.value, new Date().toISOString())

    setState({ k: 'exporting', pct: 60 })

    const compressed = encodeExportEnvelope(envelope)

    setState({ k: 'exporting', pct: 80 })

    const saved = downloadBlob(
      exportFileName(envelope.exportedAt),
      new Blob([compressed], { type: MIME_TYPE }),
    )

    if (!saved) {
      showErrorMessage('common:exportImport.exportFailed')
      return
    }

    setState({ k: 'exporting', pct: 100 })
    showSuccess('common:exportImport.exportSuccess', { count: planners.length })
  }

  /**
   * Export all planners to a compressed .danteplanner file
   */
  const handleExport = async () => {
    setState({ k: 'exporting', pct: 0 })

    const written = await attempt(writeExportFile)
    if (!written.ok) {
      console.error('Export failed:', written.error)
      showError(written.error)
    }

    setState({ k: 'idle' })
  }

  /**
   * Report an import stage that rejected the file and stand the section down
   */
  const failImport = (error: ImportError) => {
    showToast(importErrorToast(error))
    clearFileInput()
    setState({ k: 'idle' })
  }

  /**
   * Decode the picked file, partition it against the stored planners, and save
   * the ones that collide with nothing
   */
  const runImport = async (file: File) => {
    // Read file as ArrayBuffer
    const arrayBuffer = await file.arrayBuffer()

    setState({ k: 'importing', pct: 10 })

    const compressed = readGzipBytes(new Uint8Array(arrayBuffer))
    if (!compressed.ok) {
      failImport(compressed.error)
      return
    }

    setState({ k: 'importing', pct: 20 })

    const jsonString = decompressImport(compressed.value)
    if (!jsonString.ok) {
      failImport(jsonString.error)
      return
    }

    setState({ k: 'importing', pct: 40 })

    const parsed = parseImportJson(jsonString.value)
    if (!parsed.ok) {
      failImport(parsed.error)
      return
    }

    setState({ k: 'importing', pct: 50 })

    const envelope = readImportEnvelope(parsed.value)
    if (!envelope.ok) {
      failImport(envelope.error)
      return
    }

    setState({ k: 'importing', pct: 60 })

    const currentDeviceId = await getValidDeviceId(getOrCreateDeviceId)
    if (!currentDeviceId.ok) {
      showErrorMessage('common:exportImport.importFailed')
      clearFileInput()
      setState({ k: 'idle' })
      return
    }

    // Check for conflicts with existing planners
    const existingPlanners = await listLocal()
    const existingIds = new Set(existingPlanners.map((p) => p.id))

    const { conflicting, fresh } = partitionImport(
      envelope.value,
      existingIds,
      currentDeviceId.value,
    )

    const conflictItems: ConflictItem[] = []
    const nonConflicting: SaveablePlanner[] = [...fresh]

    for (const candidate of conflicting) {
      // Load existing planner for conflict comparison
      const existing = await loadFromLocal(candidate.id)
      if (existing.ok && existing.value) {
        conflictItems.push({
          id: candidate.id,
          localPlanner: existing.value,
          serverPlanner: candidate.incoming, // "Server" will be relabeled to "Imported" in dialog
        })
      } else {
        // Existing ID but failed to load - treat as non-conflicting
        nonConflicting.push(candidate.incoming)
      }
    }

    setState({ k: 'importing', pct: 80 })

    // Save non-conflicting planners immediately
    let imported = 0
    let skipped = 0
    for (const planner of nonConflicting) {
      const saved = await saveToLocal(planner)
      if (saved.ok) {
        imported++
      } else {
        skipped++
      }
    }

    // Calculate actual progress based on success ratio
    const processed = imported + skipped + conflictItems.length
    const successRatio = processed > 0 ? (imported / processed) * 100 : 0
    const pct = 80 + Math.round(successRatio * 0.2) // 80-100% based on success

    const counts = { imported, skipped, conflicts: conflictItems.length }

    // Keep the section busy while the dialog is open; progress stays put
    if (counts.conflicts > 0) {
      setState({ k: 'awaitingChoice', pct, conflicts: conflictItems })
    } else {
      clearFileInput()
      setState({ k: 'idle' })
    }

    const outcome = classifyImportOutcome(counts)
    if (outcome) {
      const descriptor = IMPORT_OUTCOME_TOASTS[outcome]
      showToast(descriptor, descriptor.params(counts))
    }
  }

  /**
   * Handle file selection for import
   */
  const handleFileSelect = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    // Reset file input for re-selection
    clearFileInput()

    // Validate file extension
    if (!file.name.endsWith(EXPORT_FILE_EXTENSION)) {
      showErrorMessage('common:exportImport.invalidFileFormat')
      return
    }

    // Validate file size to prevent memory exhaustion
    if (file.size > EXPORT_MAX_FILE_SIZE) {
      showErrorMessage('common:exportImport.fileTooLarge')
      return
    }

    setState({ k: 'importing', pct: 0 })

    const run = await attempt(() => runImport(file))
    if (!run.ok) {
      console.error('Import failed:', run.error)
      showError(run.error)
      clearFileInput()
      setState({ k: 'idle' })
    }
  }

  /**
   * Write one resolved conflict to local storage, counting what each effect did
   */
  const applyResolution = async (
    conflict: ConflictItem,
    resolution: ConflictResolution,
    ctx: ConflictResolutionContext,
  ): Promise<ResolveCounts> => {
    const plan = planConflictResolution(
      resolution.choice,
      {
        forkSide: 'incoming',
        forkTitle:
          conflict.serverPlanner.metadata.title ||
          t('planner:pages.plannerMD.untitled', 'Untitled'),
      },
      ctx,
    )

    let saved = 0
    let errors = 0

    for (const effect of plan) {
      switch (effect.kind) {
        case 'keepLocal':
          // Keep local — the local planner already exists, nothing to write
          break
        case 'adoptIncoming': {
          // Use imported - save the imported planner
          const adopted = await saveToLocal(conflict.serverPlanner)
          if (adopted.ok) {
            saved++
          } else {
            errors++
          }
          break
        }
        case 'forkCopy': {
          const copyPlanner: SaveablePlanner = {
            ...conflict.serverPlanner,
            metadata: {
              ...conflict.serverPlanner.metadata,
              id: effect.metadata.id,
              title: sanitizePlannerTitle(effect.metadata.title),
              deviceId: effect.metadata.deviceId,
              // The original keeps the publication; a copy of it starts unpublished.
              published: false,
            },
          }
          const copied = await saveToLocal(copyPlanner)
          if (copied.ok) {
            saved++
          } else {
            errors++
          }
          break
        }
        default:
          assertNever(effect)
      }
    }

    return { saved, errors }
  }

  /**
   * Handle conflict resolution from BatchConflictDialog
   */
  const handleConflictResolve = async (resolutions: ConflictResolution[]) => {
    setState({ k: 'resolving', pct: progress, conflicts })

    const deviceId = await getValidDeviceId(getOrCreateDeviceId)
    if (!deviceId.ok) {
      showErrorMessage('common:exportImport.importFailed')
      setState({ k: 'idle' })
      return
    }

    const resolutionContext = {
      deviceId: deviceId.value,
      now: new Date().toISOString(),
      newId: generateUUID,
      copyTitle: (title: string) =>
        t('planner:pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title }),
    }

    let saved = 0
    let errors = 0

    for (const resolution of resolutions) {
      const conflict = conflicts.find((c) => c.id === resolution.id)
      if (!conflict) continue

      const applied = await attempt(() => applyResolution(conflict, resolution, resolutionContext))
      if (applied.ok) {
        saved += applied.value.saved
        errors += applied.value.errors
      } else {
        console.error('Conflict resolution error:', applied.error)
        errors++
      }
    }

    clearFileInput()
    setState({ k: 'idle' })

    const counts = { saved, errors }
    const descriptor = RESOLVE_OUTCOME_TOASTS[classifyResolveOutcome(counts)]
    showToast(descriptor, descriptor.params(counts))
  }

  /**
   * Closing the dialog cancels the import's conflict step.
   *
   * The section owns a run, not a pending list: left in `awaitingChoice` behind a
   * closed dialog it would refuse every later export and import.
   */
  const handleConflictDismiss = () => {
    clearFileInput()
    setState({ k: 'idle' })

    const counts = { saved: 0, errors: 0 }
    const descriptor = RESOLVE_OUTCOME_TOASTS[classifyResolveOutcome(counts)]
    showToast(descriptor, descriptor.params(counts))
  }

  return (
    <div className="space-y-4">
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>
        {t('exportImport.title', 'Export / Import')}
      </h2>

      <p className={SECTION_STYLES.TEXT.caption}>
        {t(
          'exportImport.description',
          'Backup your planners to a file or restore from a previous backup. No server interaction.',
        )}
      </p>

      {/* Progress indicator */}
      {isProcessing && progress > 0 && (
        <div className="h-2 w-full bg-muted rounded-full overflow-hidden">
          <div
            className="h-full bg-primary transition-all duration-200"
            style={{ width: `${progress}%` }}
          />
        </div>
      )}

      {/* Action buttons */}
      <div className="flex gap-3">
        <Button variant="outline" onClick={handleExport} disabled={isProcessing}>
          {state.k === 'exporting'
            ? t('exportImport.exporting', 'Exporting...')
            : t('exportImport.export', 'Export')}
        </Button>

        <Button
          variant="outline"
          onClick={() => fileInputRef.current?.click()}
          disabled={isProcessing}
        >
          {isProcessing && state.k !== 'exporting'
            ? t('exportImport.importing', 'Importing...')
            : t('exportImport.import', 'Import')}
        </Button>

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          accept={EXPORT_FILE_EXTENSION}
          onChange={handleFileSelect}
          className="hidden"
        />
      </div>

      {/* Conflict resolution dialog */}
      <BatchConflictDialog
        open={conflicts.length >= 1}
        conflicts={conflicts}
        onResolve={handleConflictResolve}
        isResolving={state.k === 'resolving'}
        onDismiss={handleConflictDismiss}
      />
    </div>
  )
}

/**
 * Export/Import section with Suspense boundary.
 * Public component for use in SettingsPage.
 */
export function PlannerExportImportSection() {
  return (
    <Suspense fallback={<PlannerExportImportSectionSkeleton />}>
      <PlannerExportImportSectionContent />
    </Suspense>
  )
}

/**
 * Loading skeleton for export/import section.
 */
function PlannerExportImportSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-32" />
      <Skeleton className="h-4 w-64" />
      <div className="flex gap-3">
        <Skeleton className="h-9 w-20" />
        <Skeleton className="h-9 w-20" />
      </div>
    </div>
  )
}
