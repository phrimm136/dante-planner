import { describe, it, expect } from 'vitest'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { createFontAdvanceQueryOptions, type FontTableLanguage } from '../useFontAdvances'

const LANGUAGES: FontTableLanguage[] = ['KR', 'EN', 'JP', 'CN']

describe('createFontAdvanceQueryOptions', () => {
  it.each(LANGUAGES)('loads the %s description and cost tables', async (language) => {
    const client = createTestQueryClient()

    const description = await client.fetchQuery(
      createFontAdvanceQueryOptions('description', language),
    )
    const cost = await client.fetchQuery(createFontAdvanceQueryOptions('cost', language))

    expect(description.lineHeight).toBeGreaterThan(0)
    expect(cost.face).toBe('ExcelsiorSans SDF')
  })

  it('keeps the name tables at the language root', async () => {
    const name = await createTestQueryClient().fetchQuery(
      createFontAdvanceQueryOptions('name', 'KR'),
    )

    expect(name.face).toBe('KOTRA_BOLD SDF')
  })

  it('keeps the cost face descent line above the baseline, as the asset signs it', async () => {
    const cost = await createTestQueryClient().fetchQuery(
      createFontAdvanceQueryOptions('cost', 'KR'),
    )

    expect(cost.descender).toBeCloseTo(-0.240234, 6)
  })

  it('fails for a language whose table is not shipped', async () => {
    await expect(
      createTestQueryClient().fetchQuery(
        createFontAdvanceQueryOptions('description', 'XX' as FontTableLanguage),
      ),
    ).rejects.toThrow(/description\/XX\.json/)
  })
})
