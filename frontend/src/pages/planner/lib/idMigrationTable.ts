import { z } from 'zod'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

const IdMigrationEntrySchema = z
  .object({
    rename: z.record(z.string(), z.string()).default({}),
    drop: z.array(z.string()).default([]),
  })
  .strict()
  .superRefine((entry, ctx) => {
    const dropped = new Set(entry.drop)
    for (const [from, to] of Object.entries(entry.rename)) {
      if (to in entry.rename || dropped.has(to)) {
        ctx.addIssue({
          code: 'custom',
          message: `rename target ${to} (from ${from}) is itself renamed or dropped`,
          path: ['rename', from],
        })
      }
      if (dropped.has(from)) {
        ctx.addIssue({
          code: 'custom',
          message: `${from} is both renamed and dropped`,
          path: ['rename', from],
        })
      }
    }
  })

export type IdMigrationEntry = z.infer<typeof IdMigrationEntrySchema>

function renameOnlyEntrySchema(entity: string) {
  return IdMigrationEntrySchema.refine((entry) => entry.drop.length === 0, {
    message: `${entity} cannot be dropped, only renamed`,
    path: ['drop'],
  })
}

export const IdMigrationTableSchema = z
  .object({
    identity: renameOnlyEntrySchema('an identity').optional(),
    ego: IdMigrationEntrySchema.optional(),
    egoGift: IdMigrationEntrySchema.optional(),
    themePack: renameOnlyEntrySchema('a theme pack').optional(),
    startBuff: IdMigrationEntrySchema.optional(),
  })
  .strict()

export type IdMigrationTable = z.infer<typeof IdMigrationTableSchema>

export const EMPTY_ID_MIGRATION_TABLE: IdMigrationTable = {}

type JsonModuleLoaders = Record<string, () => Promise<unknown>>

const ID_MIGRATION_MODULES: JsonModuleLoaders = import.meta.glob('@static/data/idMigrations.json')

export function idMigrationImporter(
  modules: JsonModuleLoaders,
): () => Promise<{ default: unknown }> {
  const [load] = Object.values(modules)
  if (load === undefined) return () => Promise.resolve({ default: EMPTY_ID_MIGRATION_TABLE })
  return () => load() as Promise<{ default: unknown }>
}

export const idMigrationQueryKeys = {
  table: ['idMigrations'] as const,
}

export function idMigrationTableQueryOptions(modules: JsonModuleLoaders = ID_MIGRATION_MODULES) {
  return createStaticDataQueryOptions(
    idMigrationQueryKeys.table,
    idMigrationImporter(modules),
    IdMigrationTableSchema,
    'idMigrations',
  )
}
