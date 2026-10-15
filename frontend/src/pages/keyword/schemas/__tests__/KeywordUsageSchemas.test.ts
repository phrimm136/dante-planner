import { describe, expect, it } from 'vitest'

import keywordUsageList from '@static/data/keywordUsageList.json'
import { KeywordUsageListSchema } from '../KeywordUsageSchemas'

describe('KeywordUsageListSchema', () => {
  it('parses the shipped keyword usage list', () => {
    const parsed = KeywordUsageListSchema.parse(keywordUsageList)
    expect(parsed.Burst?.sinnerEvent).toHaveLength(8)
    expect(parsed.Burst?.enemy).toHaveLength(212)
    expect(parsed.Burst?.assistant).toHaveLength(4)
  })

  it('rejects a battle family outside the four lists', () => {
    const result = KeywordUsageListSchema.safeParse({
      Burst: { identities: [], enemy: [], assistant: [], sinnerEvent: [], abnormality: [] },
    })
    expect(result.success).toBe(false)
  })
})
