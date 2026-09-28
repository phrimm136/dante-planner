import fc from 'fast-check'

import { FLOOR_RULE_TABLE } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'

export const THEME_PACK_POOL = ['1001', '1002', '1003', '1004', '1005', '1006'] as const
export const GIFT_POOL = ['9001', '9002', '9003'] as const
export const MAX_RAW_FLOORS = 17
export const MAX_GIFTS = 4
export const MAX_APPENDED_FLOORS = 5
export const FILLER_FLOORS = 15

export type RawCase = { raw: unknown; category: MDCategory; stage: FloorRuleStage }

const weighted = <T>(weight: number, arbitrary: fc.Arbitrary<T>) => ({ arbitrary, weight })

const present = <T>(arbitrary: fc.Arbitrary<T>, presentToAbsent: number) =>
  fc.option(arbitrary, { nil: undefined, freq: presentToAbsent + 1 })

export const rawThemePackId: fc.Arbitrary<unknown> = fc.oneof(
  weighted(96, fc.constantFrom(...THEME_PACK_POOL)),
  weighted(2, fc.constant('')),
  weighted(2, fc.constant(null)),
  weighted(
    1,
    fc.constantFrom(...THEME_PACK_POOL).map((id) => Number(id)),
  ),
)

export const rawDifficulty: fc.Arbitrary<unknown> = fc.oneof(
  weighted(98, fc.integer({ min: 0, max: 3 })),
  weighted(1, fc.constant(null)),
  weighted(1, fc.constantFrom<unknown>(1.5, '1', true)),
)

export const rawGiftIds: fc.Arbitrary<unknown> = fc.oneof(
  weighted(97, fc.array(fc.constantFrom(...GIFT_POOL), { maxLength: MAX_GIFTS })),
  weighted(1, fc.constant(null)),
  weighted(1, fc.constant('9001')),
  weighted(1, fc.constantFrom<unknown[]>([9001], ['9001', 9002])),
)

const floorObject: fc.Arbitrary<Record<string, unknown>> = fc
  .tuple(present(rawThemePackId, 6), present(rawDifficulty, 6), present(rawGiftIds, 3))
  .map(([themePackId, difficulty, giftIds]) =>
    Object.fromEntries(
      Object.entries({ themePackId, difficulty, giftIds }).filter(
        ([, value]) => value !== undefined,
      ),
    ),
  )

const nonObjectFloor: fc.Arbitrary<unknown> = fc.constantFrom<unknown>(null, 7, '1001', [], true)

export const rawFloor: fc.Arbitrary<unknown> = fc.oneof(
  weighted(99, floorObject),
  weighted(1, nonObjectFloor),
)

export const rawFloorSelections: fc.Arbitrary<unknown[]> = fc.array(rawFloor, {
  maxLength: MAX_RAW_FLOORS,
  size: 'max',
})

const nonArrayFloorSelections: fc.Arbitrary<unknown> = fc.constantFrom<unknown>(
  null,
  {},
  'floors',
  5,
)

export const rawFloorSelectionsOrGarbage: fc.Arbitrary<unknown> = fc.oneof(
  weighted(20, rawFloorSelections),
  weighted(1, nonArrayFloorSelections),
)

export const category: fc.Arbitrary<MDCategory> = fc.constantFrom(
  ...(Object.keys(FLOOR_RULE_TABLE.categories) as MDCategory[]),
)

export const stage: fc.Arbitrary<FloorRuleStage> = fc.constantFrom(...FLOOR_RULE_TABLE.stages)

export const rawCase: fc.Arbitrary<RawCase> = fc.record({
  raw: rawFloorSelectionsOrGarbage,
  category,
  stage,
})

export const pastCountFloor: fc.Arbitrary<unknown> = fc.oneof(rawFloor, fc.jsonValue())

const anyValue = fc.anything({
  withBoxedValues: true,
  withNullPrototype: true,
  withSparseArray: true,
  withObjectString: true,
})

const anyFloor: fc.Arbitrary<unknown> = fc.oneof(
  anyValue,
  fc.record(
    { themePackId: anyValue, difficulty: anyValue, giftIds: anyValue },
    { requiredKeys: [] },
  ),
)

export const anyRaw: fc.Arbitrary<unknown> = fc.oneof(
  rawFloorSelectionsOrGarbage,
  anyValue,
  fc.array(anyFloor, { maxLength: MAX_RAW_FLOORS }),
)
