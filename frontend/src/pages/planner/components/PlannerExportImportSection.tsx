import { Suspense, useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'
import { gzip, ungzip } from 'pako'
import DOMPurify from 'dompurify'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { BatchConflictDialog } from './BatchConflictDialog'
import { usePlannerStorage } from '../hooks/usePlannerStorage'
import {
  EXPORT_VERSION,
  EXPORT_FILE_EXTENSION,
  EXPORT_MAX_FILE_SIZE,
  SECTION_STYLES,
} from '@/lib/constants'
import { downloadBlob } from '@/lib/downloadBlob'
import { generateUUID } from '@/lib/uuid'
import { assertNever } from '@/lib/utils'
import { GZIP_OS_BYTE_OFFSET, GZIP_OS_TOPS20 } from '../lib/deckCode'
import { ExportEnvelopeSchema } from '../schemas/PlannerSchemas'
import {
  ABORT,
  IMPORT_OUTCOME_TOASTS,
  ImportAbortError,
  RESOLVE_OUTCOME_TOASTS,
  classifyImportOutcome,
  classifyResolveOutcome,
  getValidDeviceId,
  importAbortToast,
  step,
} from '../lib/plannerExportImport'

import type { ConflictItem, ConflictResolution } from './BatchConflictDialog'
import type { ExportEnvelope, PlannerExportItem, SaveablePlanner } from '../types/PlannerTypes'
import type { ToastDescriptor } from '../lib/plannerExportImport'

const MIME_TYPE = 'application/gzip'

/** Gzip magic bytes: 0x1f 0x8b */
const GZIP_MAGIC_BYTES = [0x1f, 0x8b]

/**
 * Validate gzip magic bytes at start of file
 */
function isValidGzip(data: Uint8Array): boolean {
  return data.length >= 2 && data[0] === GZIP_MAGIC_BYTES[0] && data[1] === GZIP_MAGIC_BYTES[1]
}

/**
 * Sanitize planner title to prevent XSS
 */
function sanitizeTitle(title: string): string {
  return DOMPurify.sanitize(title, { ALLOWED_TAGS: [] }).trim() || 'Untitled'
}

/**
 * Inner component that contains the export/import logic.
 * Must be wrapped in Suspense boundary.
 */
function PlannerExportImportSectionContent() {
  const { t } = useTranslation(['common', 'planner'])
  const { listLocal, loadFromLocal, saveToLocal, getOrCreateDeviceId } = usePlannerStorage()

  // State for export/import operations
  const [isExporting, setIsExporting] = useState(false)
  const [isImporting, setIsImporting] = useState(false)
  const [progress, setProgress] = useState(0)

  // State for conflict resolution
  const [conflicts, setConflicts] = useState<ConflictItem[]>([])
  const [isResolving, setIsResolving] = useState(false)

  // File input ref
  const fileInputRef = useRef<HTMLInputElement>(null)

  const showToast = (descriptor: ToastDescriptor, params?: Record<string, number>) => {
    toast[descriptor.severity](t(descriptor.key, descriptor.fallback, params))
  }

  /**
   * Reset import state (for error recovery)
   */
  const resetImportState = () => {
    setIsImporting(false)
    setProgress(0)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  /**
   * Export all planners to a compressed .danteplanner file
   */
  const handleExport = async () => {
    setIsExporting(true)
    setProgress(0)

    try {
      // Get all planner summaries
      const summaries = await listLocal()

      if (summaries.length === 0) {
        toast.info(t('exportImport.noPlannersToExport', 'No planners to export'))
        setIsExporting(false)
        return
      }

      // Load full planner data in parallel batches
      const BATCH_SIZE = 10
      const planners: PlannerExportItem[] = []

      for (let i = 0; i < summaries.length; i += BATCH_SIZE) {
        const batch = summaries.slice(i, i + BATCH_SIZE)
        const results = await Promise.all(batch.map((s) => loadFromLocal(s.id)))

        for (const loaded of results) {
          const planner = loaded.ok ? loaded.value : null
          if (planner) {
            // Strip deviceId for portability (export item has id at top level)
            planners.push({
              id: planner.metadata.id,
              metadata: {
                ...planner.metadata,
                deviceId: '', // Clear for portability
              },
              config: planner.config,
              content: planner.content,
            })
          }
        }
        setProgress(Math.round(((i + batch.length) / summaries.length) * 50))
      }

      if (planners.length === 0) {
        toast.error(t('exportImport.exportFailed', 'Export failed'))
        setIsExporting(false)
        return
      }

      // Construct export envelope
      const deviceId = await getOrCreateDeviceId()
      const envelope: ExportEnvelope = {
        exportVersion: EXPORT_VERSION,
        exportedAt: new Date().toISOString(),
        sourceDeviceId: deviceId,
        planners,
      }

      setProgress(60)

      // pako 3 ignores a `header` option on the one-shot gzip(), so the OS byte is
      // written directly into the emitted header instead.
      const jsonString = JSON.stringify(envelope)
      const compressed = gzip(jsonString)
      compressed[GZIP_OS_BYTE_OFFSET] = GZIP_OS_TOPS20

      setProgress(80)

      downloadBlob(
        `plans-${new Date().toISOString().split('T')[0]}${EXPORT_FILE_EXTENSION}`,
        new Blob([compressed], { type: MIME_TYPE }),
      )

      setProgress(100)
      toast.success(
        t('exportImport.exportSuccess', 'Exported {{count}} planners', { count: planners.length }),
      )
    } catch (error) {
      console.error('Export failed:', error)
      toast.error(t('exportImport.exportFailed', 'Export failed'))
    } finally {
      setIsExporting(false)
      setProgress(0)
    }
  }

  /**
   * Handle file selection for import
   */
  const handleFileSelect = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    // Reset file input for re-selection
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }

    // Validate file extension
    if (!file.name.endsWith(EXPORT_FILE_EXTENSION)) {
      toast.error(t('exportImport.invalidFileFormat', 'Invalid file format'))
      return
    }

    // Validate file size to prevent memory exhaustion
    if (file.size > EXPORT_MAX_FILE_SIZE) {
      toast.error(t('exportImport.fileTooLarge', 'File too large (max 10MB)'))
      return
    }

    setIsImporting(true)
    setProgress(0)

    try {
      // Read file as ArrayBuffer
      const arrayBuffer = await file.arrayBuffer()
      const compressed = new Uint8Array(arrayBuffer)

      setProgress(10)

      step(() => (isValidGzip(compressed) ? compressed : ABORT), 'invalidFileFormat')

      setProgress(20)

      const jsonString = step(() => ungzip(compressed, { toText: true }), 'decompressFailed')

      setProgress(40)

      const parsed = step<unknown>(() => JSON.parse(jsonString), 'parseFailed')

      setProgress(50)

      const envelope = step(() => {
        const validation = ExportEnvelopeSchema.safeParse(parsed)
        if (!validation.success) {
          console.error('Validation failed:', validation.error)
          return ABORT
        }
        return validation.data
      }, 'invalidFileFormat')

      step(() => (envelope.planners.length > 0 ? envelope : ABORT), 'noPlannersInFile')

      setProgress(60)

      const currentDeviceId = await getValidDeviceId(getOrCreateDeviceId)

      // Check for conflicts with existing planners
      const existingPlanners = await listLocal()
      const existingIds = new Set(existingPlanners.map((p) => p.id))

      const conflictItems: ConflictItem[] = []
      const nonConflicting: SaveablePlanner[] = []
      let skipped = 0

      for (const item of envelope.planners) {
        // Sanitize imported title to prevent XSS
        const sanitizedTitle = sanitizeTitle(item.metadata.title)

        // Reconstruct planner with current device ID and sanitized title
        const planner: SaveablePlanner = {
          metadata: {
            ...item.metadata,
            title: sanitizedTitle,
            deviceId: currentDeviceId,
          },
          config: item.config,
          content: item.content,
        }

        if (existingIds.has(item.id)) {
          // Load existing planner for conflict comparison
          const existing = await loadFromLocal(item.id)
          if (existing.ok && existing.value) {
            conflictItems.push({
              id: item.id,
              localPlanner: existing.value,
              serverPlanner: planner, // "Server" will be relabeled to "Imported" in dialog
            })
          } else {
            // Existing ID but failed to load - treat as non-conflicting
            nonConflicting.push(planner)
          }
        } else {
          nonConflicting.push(planner)
        }
      }

      setProgress(80)

      // Save non-conflicting planners immediately
      let imported = 0
      for (const planner of nonConflicting) {
        const result = await saveToLocal(planner)
        if (result.success) {
          imported++
        } else {
          skipped++
        }
      }

      // Calculate actual progress based on success ratio
      const processed = imported + skipped + conflictItems.length
      const successRatio = processed > 0 ? (imported / processed) * 100 : 0
      setProgress(80 + Math.round(successRatio * 0.2)) // 80-100% based on success

      const counts = { imported, skipped, conflicts: conflictItems.length }

      // Keep isImporting true while the dialog is open; progress stays put
      if (counts.conflicts > 0) {
        setConflicts(conflictItems)
      }

      const outcome = classifyImportOutcome(counts)
      if (outcome) {
        const descriptor = IMPORT_OUTCOME_TOASTS[outcome]
        showToast(descriptor, descriptor.params(counts))
      }

      if (counts.conflicts === 0) {
        resetImportState()
      }
    } catch (error) {
      if (error instanceof ImportAbortError) {
        showToast(importAbortToast(error.reason))
        resetImportState()
        return
      }
      console.error('Import failed:', error)
      toast.error(t('exportImport.importFailed', 'Import failed'))
      resetImportState()
    }
  }

  /**
   * Handle conflict resolution from BatchConflictDialog
   */
  const handleConflictResolve = async (resolutions: ConflictResolution[]) => {
    setIsResolving(true)

    const currentDeviceId = await getValidDeviceId(getOrCreateDeviceId)

    let saved = 0
    let errors = 0

    for (const resolution of resolutions) {
      const conflict = conflicts.find((c) => c.id === resolution.id)
      if (!conflict) continue

      try {
        switch (resolution.choice) {
          case 'overwrite':
            // Keep local — the local planner already exists, nothing to write
            break
          case 'discard': {
            // Use imported - save the imported planner
            const result = await saveToLocal(conflict.serverPlanner)
            if (result.success) {
              saved++
            } else {
              errors++
            }
            break
          }
          case 'both': {
            // Keep both - save imported with new ID and "(Copy)" suffix via i18n
            const baseTitle =
              conflict.serverPlanner.metadata.title ||
              t('planner:pages.plannerMD.untitled', 'Untitled')
            const copyTitle = t('planner:pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', {
              title: baseTitle,
            })
            const copyPlanner: SaveablePlanner = {
              metadata: {
                ...conflict.serverPlanner.metadata,
                id: generateUUID(),
                title: sanitizeTitle(copyTitle),
                deviceId: currentDeviceId,
              },
              config: conflict.serverPlanner.config,
              content: conflict.serverPlanner.content,
            }
            const result = await saveToLocal(copyPlanner)
            if (result.success) {
              saved++
            } else {
              errors++
            }
            break
          }
          default:
            assertNever(resolution.choice)
        }
      } catch (error) {
        console.error('Conflict resolution error:', error)
        errors++
      }
    }

    setIsResolving(false)
    setConflicts([])
    resetImportState()

    const counts = { saved, errors }
    const descriptor = RESOLVE_OUTCOME_TOASTS[classifyResolveOutcome(counts)]
    showToast(descriptor, descriptor.params(counts))
  }

  const isProcessing = isExporting || isImporting

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
          {isExporting
            ? t('exportImport.exporting', 'Exporting...')
            : t('exportImport.export', 'Export')}
        </Button>

        <Button
          variant="outline"
          onClick={() => fileInputRef.current?.click()}
          disabled={isProcessing}
        >
          {isImporting
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
        isResolving={isResolving}
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
