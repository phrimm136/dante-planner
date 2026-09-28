import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import { floorCount } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import { admitFloors, parseFloors } from '../floorRules'
import {
  FILLER_FLOORS,
  MAX_APPENDED_FLOORS,
  anyRaw,
  category,
  pastCountFloor,
  rawFloor,
  rawFloorSelections,
  stage,
} from './floorArbitraries'

const RUNS = 500

const admit = (raw: unknown, mdCategory: MDCategory, ruleStage: FloorRuleStage) =>
  admitFloors(parseFloors(raw, floorCount(mdCategory)), mdCategory, ruleStage)

describe('floor rules properties', () => {
  it('admits its own admitted floors, re-serialized, to the same floors with no violations', () => {
    fc.assert(
      fc.property(rawFloorSelections, category, stage, (raw, mdCategory, ruleStage) => {
        const first = admit(raw, mdCategory, ruleStage)
        if (!first.ok) return
        const reserialized: unknown = JSON.parse(JSON.stringify(first.floors))

        expect(admit(reserialized, mdCategory, ruleStage)).toEqual({
          ok: true,
          floors: first.floors,
          boundaryViolations: [],
        })
      }),
      { numRuns: RUNS },
    )
  })

  it('returns the same admission when floors are appended past the count', () => {
    fc.assert(
      fc.property(
        rawFloorSelections,
        fc.array(rawFloor, { minLength: FILLER_FLOORS, maxLength: FILLER_FLOORS }),
        fc.array(pastCountFloor, { maxLength: MAX_APPENDED_FLOORS }),
        category,
        stage,
        (raw, filler, appended, mdCategory, ruleStage) => {
          const count = floorCount(mdCategory)
          const base = [...raw, ...filler.slice(0, Math.max(0, count - raw.length))]
          const reference = admit(base.slice(0, count), mdCategory, ruleStage)

          expect(admit(base, mdCategory, ruleStage)).toEqual(reference)
          expect(admit([...base, ...appended], mdCategory, ruleStage)).toEqual(reference)
        },
      ),
      { numRuns: RUNS },
    )
  })

  it('returns an admission for any raw value without throwing', () => {
    fc.assert(
      fc.property(anyRaw, category, stage, (raw, mdCategory, ruleStage) => {
        expect(typeof admit(raw, mdCategory, ruleStage).ok).toBe('boolean')
      }),
      { numRuns: RUNS },
    )
  })
})
