import { describe, it, expect } from 'vitest'
import md6MirrorDungeon from '@static/data/MD6/mirrorDungeon.json'
import { validateData } from '@/lib/validation'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { mirrorDungeonSeasonSchema } from '../../schemas/MirrorDungeonSchemas'
import { mirrorDungeonQueryOptions } from '../useMirrorDungeonSeason'

describe('mirrorDungeonQueryOptions', () => {
  it.each([
    [6, 55],
    [7, 60],
  ])('loads MD%i with identity max level %i', async (version, identityMaxLevel) => {
    const season = await createTestQueryClient().fetchQuery(mirrorDungeonQueryOptions(version))

    expect(season.identityMaxLevel).toBe(identityMaxLevel)
  })

  it('rejects a season file whose version is not the requested one', () => {
    expect(() =>
      validateData(md6MirrorDungeon, mirrorDungeonSeasonSchema(7), 'mirrorDungeon/md7'),
    ).toThrow('[mirrorDungeon/md7] Validation failed')
  })
})
