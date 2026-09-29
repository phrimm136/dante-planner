/**
 * PlannerSchemas.test.ts
 *
 * Tests for planner schema discriminated union validation.
 * Validates MDConfigSchema, RRConfigSchema, PlannerConfigDiscriminatedSchema,
 * and the two-step validateSaveablePlanner function.
 */

import { describe, it, expect, assert } from 'vitest'
import { ZodError } from 'zod'
import {
  MDConfigSchema,
  MDPlannerContentSchema,
  RRConfigSchema,
  PlannerConfigDiscriminatedSchema,
  SaveablePlannerSchema,
  ServerPlannerBatchResponseSchema,
  ServerPlannerResponseSchema,
  ServerPlannerSummaryPageSchema,
  ServerPlannerSummarySchema,
  validateSaveablePlanner,
} from '../PlannerSchemas'
import { isMDPlanner } from '../../types/PlannerTypes'

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

const SINNER_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12']

function sinnerEquipment(sinnerKey: string, egoTypes: readonly string[] = ['ZAYIN']) {
  const sinnerIdPart = sinnerKey.padStart(2, '0')
  return {
    identity: { id: `1${sinnerIdPart}01`, level: 45, uptie: 4 },
    egos: Object.fromEntries(
      egoTypes.map((egoType) => [egoType, { id: `2${sinnerIdPart}01`, threadspin: 4 }]),
    ),
  }
}

function storedShapeContent() {
  return {
    selectedKeywords: [] as string[],
    selectedBuffIds: [] as number[],
    selectedGiftKeyword: null as string | null,
    selectedGiftIds: [] as string[],
    observationGiftIds: [] as string[],
    comprehensiveGiftIds: [] as string[],
    equipment: Object.fromEntries(
      SINNER_KEYS.map((sinnerKey) => [sinnerKey, sinnerEquipment(sinnerKey)]),
    ) as Record<string, unknown>,
    deploymentOrder: [] as number[],
    skillEAState: Object.fromEntries(
      SINNER_KEYS.map((sinnerKey) => [sinnerKey, { '0': 3, '1': 2, '2': 1 }]),
    ) as Record<string, unknown>,
    floorSelections: [] as unknown[],
    sectionNotes: {} as Record<string, unknown>,
  }
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

  const content = configType === 'MIRROR_DUNGEON' ? storedShapeContent() : {}

  return {
    metadata: {
      id: '550e8400-e29b-41d4-a716-446655440000',
      title: configType === 'MIRROR_DUNGEON' ? 'Test MD Planner' : 'Test RR Planner',
      status: 'draft' as const,
      schemaVersion: 2,
      contentVersion: 6,
      plannerType: configType,
      syncVersion: 1,
      createdAt: now,
      lastModifiedAt: now,
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
    const result = MDConfigSchema.parse(validMDConfig)
    expect(result.type).toBe('MIRROR_DUNGEON')
    expect(result.category).toBe('5F')
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
    const result = RRConfigSchema.parse(validRRConfig)
    expect(result.type).toBe('REFRACTED_RAILWAY')
    expect(result.category).toBe('RR_PLACEHOLDER')
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
    const result = PlannerConfigDiscriminatedSchema.parse(validMDConfig)
    expect(result.type).toBe('MIRROR_DUNGEON')
  })

  it('validates RR config via discriminated union', () => {
    const result = PlannerConfigDiscriminatedSchema.parse(validRRConfig)
    expect(result.type).toBe('REFRACTED_RAILWAY')
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

describe('MDPlannerContentSchema', () => {
  function withOverrides(overrides: Record<string, unknown>) {
    return { ...storedShapeContent(), ...overrides }
  }

  const accepts = (content: unknown) => MDPlannerContentSchema.safeParse(content).success

  describe('stored shape families (prod counts)', () => {
    it.each([
      [['ZAYIN']],
      [['TETH', 'ZAYIN']],
      [['HE', 'ZAYIN']],
      [['WAW', 'ZAYIN']],
      [['HE', 'TETH', 'ZAYIN']],
      [['HE', 'WAW', 'ZAYIN']],
      [['WAW', 'TETH', 'ZAYIN']],
      [['HE', 'WAW', 'TETH', 'ZAYIN']],
    ])('Given E.G.O slots %j, When parsed, Then no shape violation', (egoTypes) => {
      const content = storedShapeContent()
      content.equipment['1'] = sinnerEquipment('1', egoTypes)

      expect(MDPlannerContentSchema.safeParse(content).error?.issues).toBeUndefined()
    })

    it.each([
      ['a theme pack id', '1001'],
      ['a null theme pack', null],
    ])('Given a floor with %s, When parsed, Then no shape violation', (_label, themePackId) => {
      const content = withOverrides({
        floorSelections: [{ themePackId, difficulty: 1, giftIds: ['9001', '19002'] }],
      })

      expect(MDPlannerContentSchema.safeParse(content).error?.issues).toBeUndefined()
    })

    it('Given every list and keyword filled, When parsed, Then no shape violation', () => {
      const content = withOverrides({
        selectedKeywords: ['Combustion'],
        selectedBuffIds: [101],
        selectedGiftKeyword: 'Combustion',
        selectedGiftIds: ['9001'],
        observationGiftIds: ['9002'],
        comprehensiveGiftIds: ['19003'],
        deploymentOrder: [0, 1, 2],
        sectionNotes: { floor1: { content: { type: 'doc', content: [] } } },
      })

      expect(MDPlannerContentSchema.safeParse(content).error?.issues).toBeUndefined()
    })
  })

  it('accepts integer skillEAState slot values', () => {
    expect(accepts(storedShapeContent())).toBe(true)
  })

  it('rejects a fractional skillEAState slot value', () => {
    const content = storedShapeContent()
    content.skillEAState['1'] = { '0': 1.5, '1': 2, '2': 3 }
    expect(accepts(content)).toBe(false)
  })

  it('accepts integer selectedBuffIds', () => {
    expect(accepts(withOverrides({ selectedBuffIds: [1] }))).toBe(true)
  })

  it('rejects a fractional selectedBuffIds entry', () => {
    expect(accepts(withOverrides({ selectedBuffIds: [1.5] }))).toBe(false)
  })

  it('rejects a padded sinner key beside the canonical one', () => {
    const content = storedShapeContent()
    content.equipment['01'] = sinnerEquipment('1')
    expect(accepts(content)).toBe(false)
  })

  it('rejects a removed sinner', () => {
    const content = storedShapeContent()
    delete content.equipment['12']
    expect(accepts(content)).toBe(false)
  })

  it('rejects equipment without a ZAYIN slot', () => {
    const content = storedShapeContent()
    content.equipment['1'] = sinnerEquipment('1', ['TETH'])
    expect(accepts(content)).toBe(false)
  })

  it('rejects a skill slot outside 0-2', () => {
    const content = storedShapeContent()
    content.skillEAState['1'] = { '0': 3, '1': 2, '2': 1, '3': 0 }
    expect(accepts(content)).toBe(false)
  })

  it('rejects a blank start gift keyword', () => {
    expect(accepts(withOverrides({ selectedGiftKeyword: '' }))).toBe(false)
  })

  it('rejects a whitespace start gift keyword', () => {
    expect(accepts(withOverrides({ selectedGiftKeyword: '  ' }))).toBe(false)
  })

  it.each([
    ['a whitespace identity id', '  '],
    ['a malformed identity id', 'x'],
    ['an identity id of another entity kind', '20101'],
  ])('rejects %s', (_label, identityId) => {
    const content = storedShapeContent()
    content.equipment['1'] = {
      identity: { id: identityId, level: 45, uptie: 4 },
      egos: { ZAYIN: { id: '20101', threadspin: 4 } },
    }
    expect(accepts(content)).toBe(false)
  })

  it('accepts a well-formed identity id the game data may lack', () => {
    const content = storedShapeContent()
    content.equipment['1'] = {
      identity: { id: '10199', level: 45, uptie: 4 },
      egos: { ZAYIN: { id: '20199', threadspin: 4 } },
    }
    expect(accepts(content)).toBe(true)
  })

  it('rejects a malformed E.G.O id', () => {
    const content = storedShapeContent()
    content.equipment['1'] = {
      identity: { id: '10101', level: 45, uptie: 4 },
      egos: { ZAYIN: { id: '  ', threadspin: 4 } },
    }
    expect(accepts(content)).toBe(false)
  })

  it.each([
    ['a signed theme pack id', '+1001'],
    ['a blank theme pack id', ''],
  ])('rejects a floor with %s', (_label, themePackId) => {
    const floor = { themePackId, difficulty: 0, giftIds: [] }
    expect(accepts(withOverrides({ floorSelections: [floor] }))).toBe(false)
  })

  it('rejects a malformed floor past any category count', () => {
    const floor = { themePackId: '1001', difficulty: 0, giftIds: [] }
    const floors = [...Array.from({ length: 15 }, () => floor), 5]
    expect(accepts(withOverrides({ floorSelections: floors }))).toBe(false)
  })

  it('rejects an integer outside the safe range', () => {
    expect(accepts(withOverrides({ selectedBuffIds: [2 ** 53] }))).toBe(false)
  })

  it('rejects a section note without content, as the exported schema requires it', () => {
    expect(accepts(withOverrides({ sectionNotes: { floor1: {} } }))).toBe(false)
  })
})

describe('validateSaveablePlanner', () => {
  describe('two-step validation', () => {
    it('validates a valid MD planner', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      const result = validateSaveablePlanner(planner)
      expect(result.metadata.id).toBe(planner.metadata.id)
      assert(result.config.type === 'MIRROR_DUNGEON')
      expect(result.config.category).toBe('5F')
    })

    it('validates a valid RR planner', () => {
      const planner = createValidSaveablePlanner('REFRACTED_RAILWAY')
      const result = validateSaveablePlanner(planner)
      expect(result.metadata.id).toBe(planner.metadata.id)
      expect(result.config.type).toBe('REFRACTED_RAILWAY')
    })

    it('accepts equipment whose ego slots cover only some tiers', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      const content = storedShapeContent()
      content.equipment['1'] = sinnerEquipment('1', ['ZAYIN', 'HE'])
      planner.content = content

      const result = validateSaveablePlanner(planner)
      assert(isMDPlanner(result))
      expect(result.content.equipment['1']?.egos).toEqual({
        ZAYIN: { id: '20101', threadspin: 4 },
        HE: { id: '20101', threadspin: 4 },
      })
    })
  })

  describe('floor selections follow the content schema', () => {
    function withFloors(floorSelections: unknown[]) {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      planner.content = { ...planner.content, floorSelections }
      return planner
    }

    it('accepts a floor with a null pack', () => {
      const floor = { themePackId: null, difficulty: 1, giftIds: [] }
      const result = validateSaveablePlanner(withFloors([floor]))
      assert(isMDPlanner(result))
      expect(result.content.floorSelections).toEqual([floor])
    })

    it.each([
      ['an empty-string pack', { themePackId: '', difficulty: 1, giftIds: [] }],
      ['absent giftIds', { themePackId: '1001', difficulty: 1 }],
      ['an absent difficulty', { themePackId: '1001', giftIds: [] }],
    ])('rejects %s', (_label, floor) => {
      expect(() => validateSaveablePlanner(withFloors([floor]))).toThrow(ZodError)
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

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })

    it('rejects RR content with MD config', () => {
      const planner = createValidSaveablePlanner('REFRACTED_RAILWAY')
      // Change config to MD but keep RR content
      planner.config = {
        type: 'MIRROR_DUNGEON',
        category: '5F',
      }

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })
  })

  describe('metadata validation', () => {
    it('strips the legacy userId key persisted by pre-removal app versions', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      const stored = { ...planner, metadata: { ...planner.metadata, userId: 42 } }

      const result = validateSaveablePlanner(stored)
      expect(result.metadata).not.toHaveProperty('userId')
      expect(result.metadata.id).toBe(planner.metadata.id)
    })

    it('still rejects an unknown non-legacy metadata key', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      const stored = { ...planner, metadata: { ...planner.metadata, driftKey: true } }

      expect(() => validateSaveablePlanner(stored)).toThrow(ZodError)
    })

    it('rejects invalid UUID in metadata.id', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      planner.metadata.id = 'not-a-uuid'

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })

    it('rejects invalid status in metadata', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      ;(planner.metadata as { status: string }).status = 'invalid_status'

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })

    it('rejects invalid schemaVersion', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      planner.metadata.schemaVersion = 0 // Must be positive

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })

    it('rejects invalid plannerType in metadata', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      ;(planner.metadata as { plannerType: string }).plannerType = 'INVALID_TYPE'

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })
  })

  describe('config validation', () => {
    it('rejects invalid config type', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      ;(planner.config as { type: string }).type = 'INVALID_TYPE'

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })

    it('rejects missing config', () => {
      const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
      delete (planner as { config?: unknown }).config

      expect(() => validateSaveablePlanner(planner)).toThrow(ZodError)
    })
  })
})

// ============================================================================
// Integration Tests
// ============================================================================

describe('PlannerSchemas integration', () => {
  it('discriminated union enables type narrowing', () => {
    const mdConfig = PlannerConfigDiscriminatedSchema.parse(validMDConfig)

    // Asserting the discriminator both narrows the type and proves the parse chose that arm.
    assert(mdConfig.type === 'MIRROR_DUNGEON')
    expect(['5F', '10F', '15F']).toContain(mdConfig.category)

    const rrConfig = PlannerConfigDiscriminatedSchema.parse(validRRConfig)

    assert(rrConfig.type === 'REFRACTED_RAILWAY')
    expect(rrConfig.category).toBe('RR_PLACEHOLDER')
  })

  it('full planner roundtrip validation', () => {
    const original = createValidSaveablePlanner('MIRROR_DUNGEON')
    const validated = validateSaveablePlanner(original)

    // Structure should be preserved
    expect(validated.metadata.id).toBe(original.metadata.id)
    expect(validated.config.type).toBe(original.config.type)
    expect(validated.config.category).toBe(original.config.category)
  })
})

// ============================================================================
// Import path: storage stays tolerant, save is the gate
// ============================================================================
//
// Local-JSON import persists via savePlanner -> SaveablePlannerSchema.safeParse.
// SaveablePlannerSchema treats `content` as an opaque record, so an oversized
// section note must NOT brick the import (the deliberate "tolerant storage"
// decision).

describe('import path: oversized note tolerated by storage, gated at save', () => {
  function oversizedSectionNotes() {
    return {
      intro: {
        content: {
          type: 'doc',
          content: [{ type: 'paragraph', content: [{ type: 'text', text: 'a'.repeat(5000) }] }],
        },
      },
    }
  }

  it('SaveablePlannerSchema accepts a planner whose note exceeds the cap', () => {
    const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
    ;(planner.content as { sectionNotes: unknown }).sectionNotes = oversizedSectionNotes()

    // Import/persist must succeed — a strict refine here would discard the
    // whole imported planner (the rejected design).
    expect(SaveablePlannerSchema.safeParse(planner).success).toBe(true)
  })

  it('SaveablePlannerSchema admits a stored planner whose content the content schema rejects', () => {
    const planner = createValidSaveablePlanner('MIRROR_DUNGEON')
    const content = { ...storedShapeContent(), equipment: { '01': {} }, mystery: 1 }
    planner.content = content as unknown as typeof planner.content

    expect(MDPlannerContentSchema.safeParse(content).success).toBe(false)
    expect(SaveablePlannerSchema.safeParse(planner).success).toBe(true)
  })
})

// ============================================================================
// frontend-schemas-parse-digestless-responses
// ============================================================================
//
// The server sends no contentDigest and the client declares none. Because the
// response schemas are `.strict()`, the absence is enforced in both directions:
// a digestless payload parses, and a payload still carrying the key is
// rejected rather than tolerated.

describe('frontend-schemas-parse-digestless-responses', () => {
  const PLANNER_ID = '550e8400-e29b-41d4-a716-446655440000'

  /** A planner read/write response exactly as the digestless backend sends it. */
  function digestlessResponse() {
    return {
      id: PLANNER_ID,
      title: 'Test Planner',
      category: '5F',
      status: 'saved',
      content: '{}',
      schemaVersion: 2,
      contentVersion: 6,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      published: false,
      createdAt: '2026-01-01T00:00:00.000Z',
      lastModifiedAt: '2026-01-01T00:00:00.000Z',
    }
  }

  /** A planner summary row as the digestless backend sends it. */
  function digestlessSummary() {
    return {
      id: PLANNER_ID,
      title: 'Test Planner',
      category: '5F',
      plannerType: 'MIRROR_DUNGEON',
      status: 'saved',
      syncVersion: 1,
      lastModifiedAt: '2026-01-01T00:00:00.000Z',
    }
  }

  function summaryPage() {
    return {
      content: [digestlessSummary()],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    }
  }

  it('parses a digestless read response', () => {
    expect(ServerPlannerResponseSchema.safeParse(digestlessResponse()).success).toBe(true)
  })

  it('parses a digestless write response', () => {
    // The upsert (write) path validates against the same response schema, so a
    // write acknowledgement carries a syncVersion and nothing identifying beyond it.
    const acknowledgement = { ...digestlessResponse(), syncVersion: 7 }

    const parsed = ServerPlannerResponseSchema.parse(acknowledgement)

    expect(parsed.syncVersion).toBe(7)
    expect(parsed).not.toHaveProperty('contentDigest')
  })

  it('parses a digestless summary response', () => {
    expect(ServerPlannerSummarySchema.safeParse(digestlessSummary()).success).toBe(true)
    expect(ServerPlannerSummaryPageSchema.safeParse(summaryPage()).success).toBe(true)
  })

  it('parses a digestless batch response', () => {
    const batch = [digestlessResponse(), { ...digestlessResponse(), syncVersion: 3 }]

    expect(ServerPlannerBatchResponseSchema.safeParse(batch).success).toBe(true)
  })

  it('rejects a response that still carries contentDigest', () => {
    const withDigest = { ...digestlessResponse(), contentDigest: 'ab'.repeat(32) }

    expect(ServerPlannerResponseSchema.safeParse(withDigest).success).toBe(false)
  })

  it('rejects a summary that still carries contentDigest', () => {
    const withDigest = { ...digestlessSummary(), contentDigest: 'ab'.repeat(32) }

    expect(ServerPlannerSummarySchema.safeParse(withDigest).success).toBe(false)
  })
})
