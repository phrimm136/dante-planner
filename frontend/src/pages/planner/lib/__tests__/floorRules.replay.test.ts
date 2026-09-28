import fs from 'node:fs'
import { isDeepStrictEqual } from 'node:util'
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import { floorCount } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import { admitFloors, parseFloors } from '../floorRules'
import type { Admission } from '../floorRules'
import { rawCase } from './floorArbitraries'

type Outcome = {
  ok: boolean
  floors: { themePackId: string | null; difficulty: number | null; giftIds: string[] }[]
  violations: { code: string; path: string }[]
}

type ExchangeCase = { raw: unknown; category: MDCategory; stage: FloorRuleStage; outcome: Outcome }

const EXCHANGE_SEED = 20260928
const EXCHANGE_CASES = 200
const exchangeOut = process.env.FLOOR_EXCHANGE_OUT
const exchangeIn = process.env.FLOOR_EXCHANGE_IN

const outcomeOf = (admission: Admission): Outcome => ({
  ok: admission.ok,
  floors: admission.ok
    ? admission.floors.map((floor) => ({
        themePackId: floor.themePackId ?? null,
        difficulty: floor.difficulty ?? null,
        giftIds: [...floor.giftIds],
      }))
    : [],
  violations: (admission.ok ? admission.boundaryViolations : admission.violations).map(
    ({ code, path }) => ({ code, path }),
  ),
})

const run = (raw: unknown, category: MDCategory, stage: FloorRuleStage) =>
  outcomeOf(admitFloors(parseFloors(raw, floorCount(category)), category, stage))

describe('floor rules exchange with the backend', () => {
  it.skipIf(exchangeOut === undefined)('writes the generated cases with their outcomes', () => {
    const cases: ExchangeCase[] = fc
      .sample(rawCase, { seed: EXCHANGE_SEED, numRuns: EXCHANGE_CASES })
      .map(({ raw, category, stage }) => ({
        raw,
        category,
        stage,
        outcome: run(raw, category, stage),
      }))
    fs.writeFileSync(exchangeOut ?? '', JSON.stringify({ producer: 'frontend', cases }))

    const written = JSON.parse(fs.readFileSync(exchangeOut ?? '', 'utf-8')) as { cases: unknown[] }
    expect(written.cases).toHaveLength(EXCHANGE_CASES)
  })

  it.skipIf(exchangeIn === undefined)('reproduces every outcome the backend recorded', () => {
    const exchange = JSON.parse(fs.readFileSync(exchangeIn ?? '', 'utf-8')) as {
      producer: string
      cases: ExchangeCase[]
    }
    const mismatches = exchange.cases.flatMap((exchangeCase, caseIndex) => {
      const actual = run(exchangeCase.raw, exchangeCase.category, exchangeCase.stage)
      return isDeepStrictEqual(actual, exchangeCase.outcome)
        ? []
        : [{ caseIndex, ...exchangeCase, actual }]
    })

    expect(exchange.producer).toBe('backend')
    expect(exchange.cases).not.toHaveLength(0)
    expect(mismatches).toEqual([])
  })
})
