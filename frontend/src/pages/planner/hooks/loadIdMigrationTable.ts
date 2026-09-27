import { queryClient } from '@/lib/queryClient'
import { idMigrationTableQueryOptions } from '../lib/idMigrationTable'
import type { IdMigrationTable } from '../lib/idMigrationTable'

export function loadIdMigrationTable(): Promise<IdMigrationTable> {
  return queryClient.ensureQueryData(idMigrationTableQueryOptions())
}
