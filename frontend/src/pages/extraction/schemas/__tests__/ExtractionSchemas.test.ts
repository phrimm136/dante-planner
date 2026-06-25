import { describe, it, expect } from 'vitest'
import { ExtractionInputSchema } from '../ExtractionSchemas'

/**
 * Exercises ExtractionInputSchema — the single source of truth for the extraction input
 * shape (the type derives from it via z.infer). Locks in the corrected domain model: every
 * featured count is optional (min 0, including EGO-only banners), and a target may not
 * reference a category the banner does not feature.
 */

const validInput = {
  plannedPulls: 100,
  featuredThreeStarCount: 1,
  featuredEgoCount: 1,
  featuredAnnouncerCount: 0,
  modifiers: { allEgoCollected: false, hasAnnouncer: false },
  targets: [],
  currentPity: 0,
}

describe('ExtractionInputSchema', () => {
  it('accepts a valid input, including featuredAnnouncerCount', () => {
    expect(ExtractionInputSchema.safeParse(validInput).success).toBe(true)
  })

  it('accepts an EGO-only banner (featuredThreeStarCount = 0)', () => {
    const egoOnly = { ...validInput, featuredThreeStarCount: 0, featuredEgoCount: 2 }
    expect(ExtractionInputSchema.safeParse(egoOnly).success).toBe(true)
  })

  it('rejects unknown extra keys (strict)', () => {
    const withExtra = { ...validInput, bogusField: 1 }
    expect(ExtractionInputSchema.safeParse(withExtra).success).toBe(false)
  })

  it('rejects a 3-star target when featuredThreeStarCount is 0', () => {
    const input = {
      ...validInput,
      featuredThreeStarCount: 0,
      featuredEgoCount: 1,
      targets: [{ type: 'threeStarId', wantedCopies: 1, currentCopies: 0 }],
    }
    expect(ExtractionInputSchema.safeParse(input).success).toBe(false)
  })

  it('rejects an announcer target when featuredAnnouncerCount is 0', () => {
    const input = {
      ...validInput,
      featuredAnnouncerCount: 0,
      modifiers: { allEgoCollected: false, hasAnnouncer: true },
      targets: [{ type: 'announcer', wantedCopies: 1, currentCopies: 0 }],
    }
    expect(ExtractionInputSchema.safeParse(input).success).toBe(false)
  })
})
