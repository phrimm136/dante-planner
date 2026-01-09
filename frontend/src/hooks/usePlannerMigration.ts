import { useEffect, useState, useRef, useCallback } from 'react'
import { useAuthQuery } from './useAuthQuery'
import { usePlannerStorage } from './usePlannerStorage'
import { plannerApi } from '@/lib/plannerApi'
import type { MDCategory } from '@/lib/constants'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

/**
 * LocalStorage key for tracking migration status
 * Once migration is complete, this flag prevents re-migration
 */
const MIGRATION_DONE_KEY = 'planner-migration-done'

/**
 * Migration error codes for i18n translation
 * Components should use these codes to display localized error messages
 */
export type MigrationErrorCode =
  | 'limitExceeded'
  | 'migrationFailed'
  | null

/**
 * Return type for usePlannerMigration hook
 */
export interface PlannerMigrationResult {
  /** Whether migration is currently in progress */
  isMigrating: boolean
  /** Whether migration has been completed (or was skipped) */
  migrationDone: boolean
  /** Number of planners successfully migrated (null if not completed) */
  migratedCount: number | null
  /** Total number of planners that were attempted to migrate (null if not completed) */
  totalCount: number | null
  /** Error code if migration failed (for i18n translation) */
  errorCode: MigrationErrorCode
  /** Retry migration (for manual retry after failure) */
  retryMigration: () => void
}

/**
 * Hook for migrating local planners to server on first login
 *
 * When a guest user logs in for the first time, this hook:
 * 1. Checks for planners stored in IndexedDB
 * 2. Bulk imports them to the server
 * 3. Clears local storage after successful import
 * 4. Sets a flag to prevent re-migration
 *
 * Migration is skipped if:
 * - User is not authenticated
 * - Migration has already been completed
 * - No local planners exist
 *
 * Error Handling:
 * - Returns error codes for i18n-compatible error messages
 * - Does NOT show toast directly - component should handle via error codes
 * - On failure, allows retry via retryMigration()
 *
 * @example
 * ```tsx
 * function App() {
 *   const { t } = useTranslation()
 *   const { isMigrating, migrationDone, migratedCount, errorCode, retryMigration } = usePlannerMigration()
 *
 *   useEffect(() => {
 *     if (migratedCount !== null && migratedCount > 0) {
 *       toast.success(t('migration.success', { count: migratedCount }))
 *     }
 *     if (errorCode) {
 *       toast.error(t(`migration.errors.${errorCode}`), {
 *         action: { label: t('errors.generic.retry'), onClick: retryMigration }
 *       })
 *     }
 *   }, [migratedCount, errorCode])
 *
 *   if (isMigrating) {
 *     return <LoadingScreen message={t('migration.inProgress')} />
 *   }
 *
 *   return <MainContent />
 * }
 * ```
 */
export function usePlannerMigration(): PlannerMigrationResult {
  const { data: user } = useAuthQuery()
  const localStorage = usePlannerStorage()

  const [isMigrating, setIsMigrating] = useState(false)
  const [migrationDone, setMigrationDone] = useState(false)
  const [migratedCount, setMigratedCount] = useState<number | null>(null)
  const [totalCount, setTotalCount] = useState<number | null>(null)
  const [errorCode, setErrorCode] = useState<MigrationErrorCode>(null)

  // Prevent multiple migration attempts in same render cycle
  const migrationAttemptedRef = useRef(false)

  /**
   * Core migration logic
   */
  const performMigration = useCallback(async () => {
    if (!isClient) return
    if (!user) {
      setMigrationDone(true)
      return
    }

    // Check if already migrated (persistent flag)
    const alreadyMigrated = window.localStorage.getItem(MIGRATION_DONE_KEY)
    if (alreadyMigrated) {
      setMigrationDone(true)
      return
    }

    // Check for local planners
    const localPlanners = await localStorage.listPlanners()
    if (localPlanners.length === 0) {
      // No local planners to migrate
      window.localStorage.setItem(MIGRATION_DONE_KEY, 'true')
      setMigrationDone(true)
      return
    }

    setIsMigrating(true)
    setErrorCode(null)

    try {
      // Load full planner data for each local planner
      const plannersToImport = await Promise.all(
        localPlanners.map(async (summary) => {
          const planner = await localStorage.loadPlanner(summary.id)
          if (!planner) return null

          return {
            // Cast to MDCategory - server currently only supports MD categories
            category: planner.config.category as MDCategory,
            title: planner.content.title,
            status: planner.metadata.status,
            content: JSON.stringify(planner.content),
          }
        })
      )

      // Filter out nulls (failed loads)
      const validPlanners = plannersToImport.filter(
        (p): p is NonNullable<typeof p> => p !== null
      )

      setTotalCount(validPlanners.length)

      if (validPlanners.length > 0) {
        // Bulk import to server
        const result = await plannerApi.import({ planners: validPlanners })

        setMigratedCount(result.imported)

        // Clear local storage after successful import
        for (const summary of localPlanners) {
          await localStorage.deletePlanner(summary.id)
        }
      } else {
        setMigratedCount(0)
      }

      // Mark migration as complete
      window.localStorage.setItem(MIGRATION_DONE_KEY, 'true')
      setMigrationDone(true)
    } catch (error: unknown) {
      // Handle planner limit exceeded error
      if (
        error instanceof Error &&
        (error.message.includes('PLANNER_LIMIT_EXCEEDED') ||
          error.message.includes('limit'))
      ) {
        setErrorCode('limitExceeded')
      } else {
        setErrorCode('migrationFailed')
        console.error('Planner migration failed:', error)
      }

      // Don't mark as done on failure - allow retry
      setMigrationDone(false)
    } finally {
      setIsMigrating(false)
    }
  }, [user, localStorage])

  /**
   * Auto-run migration on mount or when user changes
   */
  useEffect(() => {
    if (!isClient) return

    // Skip if already attempted in this session
    if (migrationAttemptedRef.current) return
    migrationAttemptedRef.current = true

    performMigration()
  }, [performMigration])

  /**
   * Manual retry function
   * Resets the attempt flag and re-runs migration
   */
  const retryMigration = useCallback(() => {
    migrationAttemptedRef.current = false
    setErrorCode(null)
    performMigration()
  }, [performMigration])

  return {
    isMigrating,
    migrationDone,
    migratedCount,
    totalCount,
    errorCode,
    retryMigration,
  }
}
