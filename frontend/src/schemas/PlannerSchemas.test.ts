/**
 * PlannerSchemas.test.ts
 *
 * Tests for planner schema discriminated union validation.
 * Validates MDConfigSchema, RRConfigSchema, PlannerConfigDiscriminatedSchema,
 * and the two-step validateSaveablePlanner function.
 */

import { describe, it, expect } from 'vitest'
import { ZodError } from 'zod'
import {
  MDConfigSchema,
  RRConfigSchema,
  PlannerConfigDiscriminatedSchema,
  validateSaveablePlanner,
} from './PlannerSchemas'

// ============================================================================
// Test Fixtures
// ============================================================================

/**
 * Valid MD config fixture
 */
const validMDConfig = {
  type: 'MIRROR_DUNGEON' as const,
  category: '5F' as const,
}

/**
 * Valid RR config fixture
 */
const validRRConfig = {
  type: 'REFRACTED_RAILWAY' as const,
  category: 'RR_PLACEHOLDER' as const,
}

/**
 * Create a minimal valid SaveablePlanner fixture for testing
 */
function createValidSaveablePlanner(configType: 'MIRROR_DUNGEON' | 'REFRACTED_RAILWAY') {
  const now = new Date().toISOString()
  const config =
    configType === 'MIRROR_DUNGEON'
      ? { type: 'MIRROR_DUNGEON' as const, category: '5F' as const }
      : { type: 'REFRACTED_RAILWAY' as const, category: 'RR_PLACEHOLDER' as const }

  const content =
    configType === 'MIRROR_DUNGEON'
      ? {
          title: 'Test MD Planner',
          selectedKeywords: [],
          selectedBuffIds: [],
          selectedGiftKeyword: null,
          selectedGiftIds: [],
          observationGiftIds: [],
          comprehensiveGiftIds: [],
          equipment: {},
          deploymentOrder: [],
          skillEAState: {},
          floorSelections: [],
          sectionNotes: {},
        }
      : {
          title: 'Test RR Planner',
        }

  return {
    metadata: {
      id: '550e8400-e29b-41d4-a716-446655440000',
      status: 'draft' as const,
      schemaVersion: 1,
      contentVersion: 6,
      plannerType: configType,
      syncVersion: 1,
      createdAt: now,
      lastModifiedAt: now,
      savedAt: null,
      userId: null,
      deviceId: 'test-device-123',
    },
    config,
    content,
  }
}

// ============================================================================
// MDConfigSchema Tests
// ============================================================================

describe('MDConfigSchema', () => {
  it('validates valid MD config', () => {
    const result = MDConfigSchema.safeParse(validMDConfig)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.type).toBe('MIRROR_DUNGEON')
      expect(result.data.category).toBe('5F')
    }
  })

  it('validates all MD categories', () => {
    const categories = ['5F', '10F', '15F'] as const
    for (const category of categories) {
      const result = MDConfigSchema.safeParse({
        type: 'MIRROR_DUNGEON',
        category,
      })
      expect(result.success).toBe(true)
    }
  })

  it('rejects invalid type', () => {
    const invalidConfig = {
      type: 'INVALID_TYPE',
      category: '5F',
    }
    const result = MDConfigSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })

  it('rejects invalid category for MD', () => {
    const invalidConfig = {
      type: 'MIRROR_DUNGEON',
      category: 'INVALID_CATEGORY',
    }
    const result = MDConfigSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })

  it('rejects extra fields (strict schema)', () => {
    const invalidConfig = {
      type: 'MIRROR_DUNGEON',
      category: '5F',
      extraField: 'should fail',
    }
    const result = MDConfigSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// RRConfigSchema Tests
// ============================================================================

describe('RRConfigSchema', () => {
  it('validates valid RR config', () => {
    const result = RRConfigSchema.safeParse(validRRConfig)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.type).toBe('REFRACTED_RAILWAY')
      expect(result.data.category).toBe('RR_PLACEHOLDER')
    }
  })

  it('rejects invalid type', () => {
    const invalidConfig = {
      type: 'MIRROR_DUNGEON', // Wrong type for RR schema
      category: 'RR_PLACEHOLDER',
    }
    const result = RRConfigSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })

  it('rejects invalid category for RR', () => {
    const invalidConfig = {
      type: 'REFRACTED_RAILWAY',
      category: '5F', // MD category, not RR
    }
    const result = RRConfigSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// PlannerConfigDiscriminatedSchema Tests
// ============================================================================

describe('PlannerConfigDiscriminatedSchema', () => {
  it('validates MD config via discriminated union', () => {
    const result = PlannerConfigDiscriminatedSchema.safeParse(validMDConfig)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.type).toBe('MIRROR_DUNGEON')
    }
  })

  it('validates RR config via discriminated union', () => {
    const result = PlannerConfigDiscriminatedSchema.safeParse(validRRConfig)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.type).toBe('REFRACTED_RAILWAY')
    }
  })

  it('rejects unknown type', () => {
    const invalidConfig = {
      type: 'UNKNOWN_TYPE',
      category: '5F',
    }
    const result = PlannerConfigDiscriminatedSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })

  it('rejects cross-type category (MDCategory on RRConfig)', () => {
    const invalidConfig = {
      type: 'REFRACTED_RAILWAY',
      category: '5F', // MD category, not valid for RR
    }
    const result = PlannerConfigDiscriminatedSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })

  it('rejects cross-type category (RRCategory on MDConfig)', () => {
    const invalidConfig = {
      type: 'MIRROR_DUNGEON',
      category: 'RR_PLACEHOLDER', // RR category, not valid for MD
    }
    const result = PlannerConfigDiscriminatedSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })

  it('rejects config without type field', () => {
    const invalidConfig = {
      category: '5F',
    }
    const result = PlannerConfigDiscriminatedSchema.safeParse(invalidConfig)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// validateSaveablePlanner Tests
// ============================================================================

describe('validateSaveablePlanner', () => {
  describe('two-step validation', () => {
    it('validates valid MD planner in draft mode', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      const result = validateSaveablePlanner(planner, 'draft')
      expect(result.metadata.id).toBe(planner.metadata.id)
      expect(result.config.type).toBe('MIRROR_DUNGEON')
      if (result.config.type === 'MIRROR_DUNGEON') {
        expect(result.config.category).toBe('5F')
      }
    })

    it('validates valid RR planner in draft mode', () => {
      const planner = createValidSaveablePlanner('REFRACTED_RAILWAY')
      const result = validateSaveablePlanner(planner, 'draft')
      expect(result.metadata.id).toBe(planner.metadata.id)
      expect(result.config.type).toBe('REFRACTED_RAILWAY')
    })

    it('validates valid MD planner in save mode', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      // Add required themePackId for save mode (all floor selections need it)
      const result = validateSaveablePlanner(planner, 'save')
      expect(result.config.type).toBe('MIRROR_DUNGEON')
    })
  })

  describe('content/config type mismatch detection', () => {
    it('rejects MD content with RR config', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      // Change config to RR but keep MD content
      planner.config = {
        type: 'REFRACTED_RAILWAY',
        category: 'RR_PLACEHOLDER',
      }

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })

    it('rejects RR content with MD config', () => {
      const planner = createValidSaveablePlanner('REFRACTED_RAILWAY')
      // Change config to MD but keep RR content
      planner.config = {
        type: 'MIRROR_DUNGEON',
        category: '5F',
      }

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })
  })

  describe('metadata validation', () => {
    it('rejects invalid UUID in metadata.id', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      planner.metadata.id = 'not-a-uuid'

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })

    it('rejects invalid status in metadata', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      ;(planner.metadata as { status: string }).status = 'invalid_status'

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })

    it('rejects invalid schemaVersion', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      planner.metadata.schemaVersion = 0 // Must be positive

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })

    it('rejects invalid plannerType in metadata', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      ;(planner.metadata as { plannerType: string }).plannerType = 'INVALID_TYPE'

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })
  })

  describe('config validation', () => {
    it('rejects invalid config type', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      ;(planner.config as { type: string }).type = 'INVALID_TYPE'

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })

    it('rejects missing config', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      delete (planner as { config?: unknown }).config

      expect(() => validateSaveablePlanner(planner, 'draft')).toThrow(ZodError)
    })
  })
})

// ============================================================================
// Integration Tests
// ============================================================================

describe('PlannerSchemas integration', () => {
  it('discriminated union enables type narrowing', () => {
    const mdConfig = PlannerConfigDiscriminatedSchema.parse(validMDConfig)

    // TypeScript should narrow the type based on discriminator
    if (mdConfig.type === 'MIRROR_DUNGEON') {
      // Should have access to MD-specific category values
      expect(['5F', '10F', '15F']).toContain(mdConfig.category)
    }

    const rrConfig = PlannerConfigDiscriminatedSchema.parse(validRRConfig)

    if (rrConfig.type === 'REFRACTED_RAILWAY') {
      // Should have access to RR-specific category values
      expect(rrConfig.category).toBe('RR_PLACEHOLDER')
    }
  })

  it('full planner roundtrip validation', () => {
    const original = createValidSaveablePlanner('MIRROR_DUNGEON')
    const validated = validateSaveablePlanner(original, 'draft')

    // Structure should be preserved
    expect(validated.metadata.id).toBe(original.metadata.id)
    expect(validated.config.type).toBe(original.config.type)
    expect(validated.config.category).toBe(original.config.category)
  })
})
