import { describe, it, expect } from 'vitest'
import { createTestQueryClient } from '@/test-utils/queryClient'
import {
  startBuffCardLayoutQueryOptions,
  startBuffMiniCardLayoutQueryOptions,
} from '../useStartBuffCardLayout'

const SEASON_WITHOUT_LAYOUT = 5

describe('start-buff layout loaders', () => {
  it.each([6, 7])('loads the MD%i card and mini card layouts', async (version) => {
    const client = createTestQueryClient()

    const card = await client.fetchQuery(startBuffCardLayoutQueryOptions(version))
    const mini = await client.fetchQuery(startBuffMiniCardLayoutQueryOptions(version))

    expect(card.prefab).toBe(`[Script]StartBuffCardUIWithLevel_MD${String(version)}`)
    expect(mini.prefab).toBe('[Script]MDF_SelectedStartBuffUI')
  })

  it('fails for a season that ships no card layout', async () => {
    const client = createTestQueryClient()

    await expect(
      client.fetchQuery(startBuffCardLayoutQueryOptions(SEASON_WITHOUT_LAYOUT)),
    ).rejects.toThrow(/MD5\/startBuffCard\.json/)
    await expect(
      client.fetchQuery(startBuffMiniCardLayoutQueryOptions(SEASON_WITHOUT_LAYOUT)),
    ).rejects.toThrow(/MD5\/startBuffMiniCard\.json/)
  })
})
