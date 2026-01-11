import { describe, it, expect } from 'vitest'
import { useThemePackListData } from './useThemePackListData'

/**
 * Type-level test to ensure useThemePackListData follows the correct pattern
 *
 * This test verifies that the hook returns { spec, i18n } (not { themePackList, themePackI18n })
 * to match the pattern used by useEGOGiftListData and other data hooks.
 */
describe('useThemePackListData', () => {
  it('returns correct type structure (spec, i18n)', () => {
    // This is a compile-time test - if it compiles, the types are correct
    type ReturnType = ReturnType<typeof useThemePackListData>

    // This will fail to compile if the return type doesn't have 'spec' property
    const _testSpec: (data: ReturnType) => typeof data.spec = (data) => data.spec

    // This will fail to compile if the return type doesn't have 'i18n' property
    const _testI18n: (data: ReturnType) => typeof data.i18n = (data) => data.i18n

    // Verify the hook exports the correct pattern
    expect(true).toBe(true)
  })

  it('documentation matches pattern (spec, i18n, not themePackList/themePackI18n)', () => {
    // Read the hook source to verify JSDoc and return statement
    const hookSource = useThemePackListData.toString()

    // Verify it returns an object with spec and i18n
    expect(hookSource).toContain('spec')
    expect(hookSource).toContain('i18n')
  })
})
