import { Suspense, useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import pako from 'pako'
import DOMPurify from 'dompurify'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { BatchConflictDialog } from '@/components/dialogs/BatchConflictDialog'
import { usePlannerStorage } from '@/hooks/usePlannerStorage'
import { EXPORT_VERSION, EXPORT_FILE_EXTENSION, EXPORT_MAX_FILE_SIZE } from '@/lib/constants'
import { ExportEnvelopeSchema } from '@/schemas/PlannerSchemas'
import { isValidUUID } from '@/lib/utils'

import type { ConflictItem, ConflictResolution } from '@/components/dialogs/BatchConflictDialog'
import type { ExportEnvelope, PlannerExportItem, SaveablePlanner } from '@/types/PlannerTypes'

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
  const { t } = useTranslation(['planner', 'settings', 'common'])
  const { listPlanners, loadPlanner, savePlanner, getOrCreateDeviceId } = usePlannerStorage()

  // State for export/import operations
  const [isExporting, setIsExporting] = useState(false)
  const [isImporting, setIsImporting] = useState(false)
  const [progress, setProgress] = useState(0)

  // State for conflict resolution
  const [conflicts, setConflicts] = useState<ConflictItem[]>([])
  const [isResolving, setIsResolving] = useState(false)

  // File input ref
  const fileInputRef = useRef<HTMLInputElement>(null)

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
      const summaries = await listPlanners()

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
        const results = await Promise.all(batch.map((s) => loadPlanner(s.id)))

        for (const planner of results) {
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

      // Compress with gzip (type assertion for header option not in DefinitelyTyped)
      const jsonString = JSON.stringify(envelope)
      const compressed = pako.gzip(jsonString, { header: { os: 10 } } as pako.DeflateFunctionOptions)

      setProgress(80)

      // Create download link
      const blob = new Blob([compressed], { type: MIME_TYPE })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `plans-${new Date().toISOString().split('T')[0]}${EXPORT_FILE_EXTENSION}`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)

      setProgress(100)
      toast.success(
        t('exportImport.exportSuccess', 'Exported {{count}} planners', { count: planners.length })
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

      // Validate gzip magic bytes
      if (!isValidGzip(compressed)) {
        toast.error(t('exportImport.invalidFileFormat', 'Invalid file format'))
        resetImportState()
        return
      }

      setProgress(20)

      // Decompress
      let jsonString: string
      try {
        jsonString = pako.ungzip(compressed, { to: 'string' })
      } catch {
        toast.error(t('exportImport.decompressFailed', 'Failed to decompress file'))
        resetImportState()
        return
      }

      setProgress(40)

      // Parse JSON
      let parsed: unknown
      try {
        parsed = JSON.parse(jsonString)
      } catch {
        toast.error(t('exportImport.parseFailed', 'Failed to parse file'))
        resetImportState()
        return
      }

      setProgress(50)

      // Validate with Zod
      const validation = ExportEnvelopeSchema.safeParse(parsed)
      if (!validation.success) {
        console.error('Validation failed:', validation.error)
        toast.error(t('exportImport.invalidFileFormat', 'Invalid file format'))
        resetImportState()
        return
      }

      const envelope = validation.data

      if (envelope.planners.length === 0) {
        toast.info(t('exportImport.noPlannersInFile', 'No planners in file'))
        resetImportState()
        return
      }

      setProgress(60)

      // Get current device ID for reconstructing keys (retry with fallback)
      let currentDeviceId = await getOrCreateDeviceId()
      if (!currentDeviceId || !isValidUUID(currentDeviceId)) {
        // Retry once
        currentDeviceId = await getOrCreateDeviceId()
      }
      if (!currentDeviceId || !isValidUUID(currentDeviceId)) {
        // Final fallback: generate new UUID
        currentDeviceId = crypto.randomUUID()
        console.warn('Using fallback device ID for import:', currentDeviceId)
      }

      // Check for conflicts with existing planners
      const existingPlanners = await listPlanners()
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
          const existingPlanner = await loadPlanner(item.id)
          if (existingPlanner) {
            conflictItems.push({
              id: item.id,
              localPlanner: existingPlanner,
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
        const result = await savePlanner(planner)
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

      // If conflicts exist, open dialog (keep isImporting true until resolved)
      if (conflictItems.length > 0) {
        setConflicts(conflictItems)
        // Show partial import message
        if (imported > 0 || skipped > 0) {
          toast.info(
            t('exportImport.partialImport', 'Imported {{imported}}, {{conflicts}} conflicts', {
              imported,
              conflicts: conflictItems.length,
            })
          )
        }
        // Don't reset progress - conflicts pending
      } else {
        // No conflicts - show final result
        if (skipped > 0) {
          toast.success(
            t('exportImport.importPartialSuccess', 'Imported {{imported}}, skipped {{skipped}}', {
              imported,
              skipped,
            })
          )
        } else {
          toast.success(
            t('exportImport.importSuccess', 'Imported {{count}} planners', { count: imported })
          )
        }
        resetImportState()
      }
    } catch (error) {
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

    // Get device ID with retry and fallback
    let currentDeviceId = await getOrCreateDeviceId()
    if (!currentDeviceId || !isValidUUID(currentDeviceId)) {
      currentDeviceId = await getOrCreateDeviceId()
    }
    if (!currentDeviceId || !isValidUUID(currentDeviceId)) {
      // Final fallback: generate new UUID
      currentDeviceId = crypto.randomUUID()
      console.warn('Using fallback device ID for conflict resolution:', currentDeviceId)
    }

    let saved = 0
    let errors = 0

    for (const resolution of resolutions) {
      const conflict = conflicts.find((c) => c.id === resolution.id)
      if (!conflict) continue

      try {
        if (resolution.choice === 'overwrite') {
          // Keep local - do nothing (local already exists)
        } else if (resolution.choice === 'discard') {
          // Use imported - save the imported planner
          const result = await savePlanner(conflict.serverPlanner)
          if (result.success) {
            saved++
          } else {
            errors++
          }
        } else if (resolution.choice === 'both') {
          // Keep both - save imported with new ID and "(Copy)" suffix via i18n
          const baseTitle = conflict.serverPlanner.metadata.title || t('pages.plannerMD.untitled', 'Untitled')
          const copyTitle = t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title: baseTitle })
          const copyPlanner: SaveablePlanner = {
            metadata: {
              ...conflict.serverPlanner.metadata,
              id: crypto.randomUUID(),
              title: sanitizeTitle(copyTitle),
              deviceId: currentDeviceId,
            },
            config: conflict.serverPlanner.config,
            content: conflict.serverPlanner.content,
          }
          const result = await savePlanner(copyPlanner)
          if (result.success) {
            saved++
          } else {
            errors++
          }
        }
      } catch (error) {
        console.error('Conflict resolution error:', error)
        errors++
      }
    }

    setIsResolving(false)
    setConflicts([])
    resetImportState()

    if (errors > 0) {
      toast.warning(
        t('exportImport.resolvePartial', 'Resolved {{saved}}, {{errors}} errors', { saved, errors })
      )
    } else if (saved > 0) {
      toast.success(t('exportImport.resolveSuccess', 'Resolved {{count}} conflicts', { count: saved }))
    } else {
      toast.success(t('exportImport.resolveKeptLocal', 'Kept all local versions'))
    }
  }

  const isProcessing = isExporting || isImporting

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">
        {t('exportImport.title', 'Export / Import')}
      </h2>

      <p className="text-sm text-muted-foreground">
        {t(
          'exportImport.description',
          'Backup your planners to a file or restore from a previous backup. No server interaction.'
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
        <Button
          variant="outline"
          onClick={handleExport}
          disabled={isProcessing}
        >
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
