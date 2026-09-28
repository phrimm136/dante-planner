import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import { MD_CATEGORIES, floorCount } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import { admitFloors, parseFloors } from '../floorRules'
import type { BeErrorCode, FloorSelectionValue, ParsedFloors } from '../floorRules'

type CorpusViolation = { code: string; path: string }

type CorpusCase = {
  name: string
  category: string
  floorSelections: unknown
  expect: {
    draft: { ok: boolean; violations: CorpusViolation[] }
    publish: { ok: boolean; violations: CorpusViolation[] }
    index: { ok: boolean; floors: number[]; salvaged: number[]; violations: CorpusViolation[] }
  }
}

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../..',
)
const CORPUS_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-floor-rules.corpus.json')
const corpus = JSON.parse(fs.readFileSync(CORPUS_PATH, 'utf-8')) as { cases: CorpusCase[] }

const MODULE_CODES: readonly BeErrorCode[] = [
  'INVALID_FIELD_TYPE',
  'FLOOR_MISSING_THEME_PACK',
  'FLOOR_DUPLICATE_THEME_PACK',
  'INVALID_SEQUENCE',
  'VALUE_OUT_OF_RANGE',
  'DUPLICATE_VALUE',
]
const ID_CHECK_CODES = ['THEME_PACK_UNKNOWN_ID', 'FLOOR_UNKNOWN_GIFT_ID', 'GIFT_NOT_AFFORDABLE']
const CALLER_BOUNDARY_CODES = ['INVALID_CATEGORY']

const isModuleCode = (code: string) => (MODULE_CODES as readonly string[]).includes(code)
const isMdCategory = (category: string): category is MDCategory =>
  (MD_CATEGORIES as readonly string[]).includes(category)

const pairs = (violations: readonly CorpusViolation[]) =>
  violations.map(({ code, path: violationPath }) => [code, violationPath])

const moduleCases = corpus.cases.filter((c) => isMdCategory(c.category))
const callerCases = corpus.cases.filter((c) => !isMdCategory(c.category))
const ruleStages: FloorRuleStage[] = ['draft', 'publish']

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const scalarText = (value: unknown) =>
  typeof value === 'string' ? value : typeof value === 'number' ? String(value) : undefined

function salvageOf(rawFloor: unknown): FloorSelectionValue {
  if (!isRecord(rawFloor)) return { giftIds: [] }
  const themePackId = scalarText(rawFloor.themePackId)
  const giftIds: unknown[] = Array.isArray(rawFloor.giftIds) ? rawFloor.giftIds : []
  return {
    ...(themePackId ? { themePackId } : {}),
    ...(Number.isInteger(rawFloor.difficulty) ? { difficulty: rawFloor.difficulty as number } : {}),
    giftIds: giftIds.flatMap((giftId) => scalarText(giftId) ?? []),
  }
}

function admittedIndices(parsed: ParsedFloors, admitted: readonly FloorSelectionValue[]) {
  const passed: number[] = []
  const salvaged: number[] = []
  let floorIndex = 0
  for (const floor of admitted) {
    while (
      floorIndex < parsed.floors.length &&
      parsed.floors[floorIndex] !== floor &&
      parsed.salvage[floorIndex] !== floor
    ) {
      floorIndex += 1
    }
    if (parsed.floors[floorIndex] === floor) passed.push(floorIndex)
    else if (parsed.salvage[floorIndex] === floor) salvaged.push(floorIndex)
    else throw new Error('admitted floor is neither parsed nor salvaged, or out of order')
    floorIndex += 1
  }
  return { passed, salvaged }
}

function admit(c: CorpusCase, stage: FloorRuleStage) {
  const category = c.category as MDCategory
  const parsed = parseFloors(c.floorSelections, floorCount(category))
  return { parsed, admission: admitFloors(parsed, category, stage) }
}

describe('floor rules corpus', () => {
  it('holds 55 cases, 54 of them for the module', () => {
    expect(corpus.cases).toHaveLength(55)
    expect(moduleCases).toHaveLength(54)
  })

  it('records only module codes, id-check codes and caller boundary codes', () => {
    const known = new Set<string>([...MODULE_CODES, ...ID_CHECK_CODES, ...CALLER_BOUNDARY_CODES])
    const recorded = corpus.cases.flatMap((c) =>
      [c.expect.draft, c.expect.publish, c.expect.index].flatMap((e) =>
        e.violations.map((v) => v.code),
      ),
    )
    expect(recorded.filter((code) => !known.has(code))).toEqual([])
  })

  it('leaves only the unknown-category case to the caller, which rejects it at category', () => {
    expect(callerCases.map((c) => c.name)).toEqual(['boundary-unknown-category'])
    for (const c of callerCases) {
      for (const stage of ['draft', 'publish', 'index'] as const) {
        expect(pairs(c.expect[stage].violations)).toEqual([['INVALID_CATEGORY', 'category']])
      }
    }
  })

  describe.each(ruleStages)('%s', (stage) => {
    it.each(moduleCases.map((c) => [c.name, c] as const))('%s', (_name, c) => {
      const expected = c.expect[stage].violations.filter((v) => isModuleCode(v.code))
      const { admission } = admit(c, stage)
      expect({
        ok: admission.ok,
        violations: admission.ok ? [] : pairs(admission.violations),
      }).toEqual({ ok: expected.length === 0, violations: pairs(expected) })
    })
  })

  describe('index', () => {
    it.each(moduleCases.map((c) => [c.name, c] as const))('%s', (_name, c) => {
      const { parsed, admission } = admit(c, 'index')
      const admitted = admission.ok ? admission.floors : []
      const boundaryViolations = admission.ok ? admission.boundaryViolations : admission.violations
      const { passed, salvaged } = admittedIndices(parsed, admitted)
      expect(admission.ok).toBe(true)
      expect({
        ok: boundaryViolations.length === 0,
        floors: passed,
        salvaged,
        violations: pairs(boundaryViolations),
      }).toEqual({
        ok: c.expect.index.ok,
        floors: c.expect.index.floors,
        salvaged: c.expect.index.salvaged,
        violations: pairs(c.expect.index.violations),
      })
      const rawFloors = c.floorSelections as unknown[]
      expect(salvaged.map((floorIndex) => parsed.salvage[floorIndex])).toEqual(
        salvaged.map((floorIndex) => salvageOf(rawFloors[floorIndex])),
      )
    })
  })
})
