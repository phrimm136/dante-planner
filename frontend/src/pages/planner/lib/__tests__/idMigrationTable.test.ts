import { describe, it, expect } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import {
  EMPTY_ID_MIGRATION_TABLE,
  IdMigrationTableSchema,
  idMigrationTableQueryOptions,
} from '../idMigrationTable'

function fetchTable(modules: Record<string, () => Promise<unknown>>) {
  return new QueryClient().fetchQuery(idMigrationTableQueryOptions(modules))
}

describe('idMigrationTableQueryOptions', () => {
  it('loads whatever the static tree ships, the file being optional', async () => {
    await expect(new QueryClient().fetchQuery(idMigrationTableQueryOptions())).resolves.toBeTypeOf(
      'object',
    )
  })

  it('reads an absent idMigrations.json as the empty table', async () => {
    await expect(fetchTable({})).resolves.toEqual(EMPTY_ID_MIGRATION_TABLE)
  })

  it('reads the file the glob matched', async () => {
    const table = { egoGift: { rename: { '9247': '9300' }, drop: ['9666'] } }

    await expect(
      fetchTable({ '../static/data/idMigrations.json': async () => ({ default: table }) }),
    ).resolves.toEqual(table)
  })

  it('fills an omitted rename or drop list with an empty one', async () => {
    await expect(
      fetchTable({
        '../static/data/idMigrations.json': async () => ({
          default: { ego: { drop: ['20299'] } },
        }),
      }),
    ).resolves.toEqual({ ego: { rename: {}, drop: ['20299'] } })
  })
})

describe('IdMigrationTableSchema', () => {
  it('rejects a rename chain, which one pass could not apply idempotently', () => {
    const chained = { ego: { rename: { '20101': '20102', '20102': '20103' }, drop: [] } }

    expect(IdMigrationTableSchema.safeParse(chained).success).toBe(false)
  })

  it('rejects an identity drop, an identity having no empty form', () => {
    const identityDrop = { identity: { rename: {}, drop: ['10199'] } }

    expect(IdMigrationTableSchema.safeParse(identityDrop).success).toBe(false)
  })

  it('rejects a theme-pack drop, a floor having no empty form', () => {
    const themePackDrop = { themePack: { rename: {}, drop: ['1001'] } }

    const parsed = IdMigrationTableSchema.safeParse(themePackDrop)

    expect(parsed.success).toBe(false)
    expect(parsed.error?.issues).toEqual([
      expect.objectContaining({
        path: ['themePack', 'drop'],
        message: expect.stringContaining('theme pack'),
      }),
    ])
  })

  it('accepts a theme-pack rename', () => {
    const themePackRename = { themePack: { rename: { '1001': '1002' } } }

    expect(IdMigrationTableSchema.safeParse(themePackRename).success).toBe(true)
  })

  it('accepts an identity rename', () => {
    const identityRename = { identity: { rename: { '10199': '10101' } } }

    expect(IdMigrationTableSchema.safeParse(identityRename).success).toBe(true)
  })

  it('rejects a rename onto a dropped id', () => {
    const intoDrop = { egoGift: { rename: { '9247': '9300' }, drop: ['9300'] } }

    expect(IdMigrationTableSchema.safeParse(intoDrop).success).toBe(false)
  })

  it('rejects an entity type the planners do not reference', () => {
    expect(IdMigrationTableSchema.safeParse({ keyword: { rename: {}, drop: [] } }).success).toBe(
      false,
    )
  })
})
