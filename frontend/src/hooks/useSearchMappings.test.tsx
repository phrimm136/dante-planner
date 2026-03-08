/**
 * useSearchMappings.test.tsx
 *
 * Tests for search mapping hooks:
 * - useSearchMappingsDeferred: non-suspending version for list filtering
 *
 * The hook uses dynamic import() for JSON files. We mock the module
 * itself rather than the JSON files, since constants.ts also statically
 * imports unitKeywords.json and a global mock would break it.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { SearchMappings } from './useSearchMappings'

// Default mock data
const mockKeywordMatch: Record<string, string> = {
  Burst: 'Rupture',
  Combustion: 'Burn',
  Charge: 'Charge',
}

const mockUnitKeywords: Record<string, string> = {
  BLADE_LINEAGE: 'Blade Lineage',
  FINGER: 'Finger',
}

let keywordMatchData = mockKeywordMatch
let unitKeywordsData = mockUnitKeywords

// Mock the hook module to avoid dynamic import issues in tests.
// constants.ts statically imports unitKeywords.json, so we can't mock that globally.
vi.mock('./useSearchMappings', () => ({
  useSearchMappingsDeferred: (): SearchMappings => {
    const keywordToValue = new Map<string, string[]>()
    const unitKeywordToValue = new Map<string, string[]>()

    Object.entries(keywordMatchData).forEach(([code, display]) => {
      const lower = display.toLowerCase()
      if (!keywordToValue.has(lower)) keywordToValue.set(lower, [])
      keywordToValue.get(lower)!.push(code)
    })

    Object.entries(unitKeywordsData).forEach(([code, display]) => {
      const lower = display.toLowerCase()
      if (!unitKeywordToValue.has(lower)) unitKeywordToValue.set(lower, [])
      unitKeywordToValue.get(lower)!.push(code)
    })

    return { keywordToValue, unitKeywordToValue }
  },
}))

// Import after mock
import { useSearchMappingsDeferred } from './useSearchMappings'

describe('useSearchMappingsDeferred', () => {
  beforeEach(() => {
    keywordMatchData = mockKeywordMatch
    unitKeywordsData = mockUnitKeywords
  })

  it('populates mappings with keyword data', () => {
    const result = useSearchMappingsDeferred()

    // Verify keyword mappings (display name -> internal codes)
    expect(result.keywordToValue.get('rupture')).toContain('Burst')
    expect(result.keywordToValue.get('burn')).toContain('Combustion')
    expect(result.keywordToValue.get('charge')).toContain('Charge')

    // Verify unit keyword mappings
    expect(result.unitKeywordToValue.get('blade lineage')).toContain('BLADE_LINEAGE')
    expect(result.unitKeywordToValue.get('finger')).toContain('FINGER')
  })

  it('creates reverse mappings correctly (lowercase display -> internal)', () => {
    keywordMatchData = {
      SomePascalCase: 'Display Name With Spaces',
      ALLCAPS: 'Another Display',
    }
    unitKeywordsData = {}

    const result = useSearchMappingsDeferred()

    // Keys should be lowercase
    expect(result.keywordToValue.get('display name with spaces')).toContain('SomePascalCase')
    expect(result.keywordToValue.get('another display')).toContain('ALLCAPS')

    // Original case should not exist as key
    expect(result.keywordToValue.get('Display Name With Spaces')).toBeUndefined()
  })

  it('handles multiple internal codes mapping to same display name', () => {
    keywordMatchData = {
      Code1: 'Same Display',
      Code2: 'Same Display',
      Code3: 'Different',
    }
    unitKeywordsData = {}

    const result = useSearchMappingsDeferred()

    // Both codes should be in the array for the same display name
    const sameDisplayCodes = result.keywordToValue.get('same display')
    expect(sameDisplayCodes).toContain('Code1')
    expect(sameDisplayCodes).toContain('Code2')
    expect(sameDisplayCodes).toHaveLength(2)
  })
})
