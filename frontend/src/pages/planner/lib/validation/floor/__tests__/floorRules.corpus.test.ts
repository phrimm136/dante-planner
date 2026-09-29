import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import { FLOOR_RULE_TABLE, MD_CATEGORIES } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import { renderContentPath } from '../../contentPath'
import { admitFloors, parseFloors } from '../floorRules'
import type { ParsedFloor, ParsedFloors } from '../floorRules'
import { floorReferenceViolations } from '../floorReferences'
import type { FloorReferenceData } from '../floorReferences'
import { byPathThenCode } from '../../violation'
import type { FloorViolationCode, Violation } from '../../violation'
import { outcomeFloor } from './floorOutcome'
import type { OutcomeFloor } from './floorOutcome'

type CorpusViolation = { code: string; path: string }

type CorpusCase = {
  name: string
  category: string
  floorSelections: unknown
  expect: {
    draft: { ok: boolean; violations: CorpusViolation[] }
    publish: { ok: boolean; violations: CorpusViolation[] }
    index: {
      ok: boolean
      floors: number[]
      salvaged: number[]
      admitted: OutcomeFloor[]
      violations: CorpusViolation[]
    }
  }
}

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../../../..',
)
const CORPUS_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-floor-rules.corpus.json')
const corpus = JSON.parse(fs.readFileSync(CORPUS_PATH, 'utf-8')) as {
  gameData: { themePackIds: string[]; egoGiftThemePacks: Record<string, string[]> }
  cases: CorpusCase[]
}

const themePackIds = new Set(corpus.gameData.themePackIds)
const giftThemePacks = new Map(Object.entries(corpus.gameData.egoGiftThemePacks))

const corpusGameData: FloorReferenceData = {
  hasThemePack: (themePackId) => themePackIds.has(themePackId),
  hasEgoGift: (giftId) => giftThemePacks.has(giftId),
  isGiftAffordableForThemePack: (giftId, themePackId) => {
    const packs = giftThemePacks.get(giftId) ?? []
    return packs.length === 0 || packs.includes(themePackId)
  },
}

const MODULE_CODES: readonly FloorViolationCode[] = [
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

const pairsOf = (violations: readonly Violation[]) =>
  violations.map(({ code, path: violationPath }) => [code, renderContentPath(violationPath)])

const moduleCases = corpus.cases.filter((c) => isMdCategory(c.category))
const callerCases = corpus.cases.filter((c) => !isMdCategory(c.category))
const ruleStages: FloorRuleStage[] = ['draft', 'publish']

const indicesOf = (parsed: ParsedFloors, kind: ParsedFloor['kind']) =>
  parsed.floors.flatMap((floor, floorIndex) => (floor.kind === kind ? [floorIndex] : []))

function admit(c: CorpusCase, stage: FloorRuleStage) {
  const parsed = parseFloors(
    c.floorSelections,
    FLOOR_RULE_TABLE.categories[c.category as MDCategory],
  )
  return { parsed, admission: admitFloors(parsed, stage) }
}

describe('floor rules corpus', () => {
  it('holds 57 cases, 56 of them for the module', () => {
    expect(corpus.cases).toHaveLength(57)
    expect(moduleCases).toHaveLength(56)
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

  it('orders non-string gifts at 2 and 10 by number, 2 first', () => {
    const giftIds = [
      '9001',
      '9002',
      9003,
      '9004',
      '9005',
      '9006',
      '9007',
      '9008',
      '9009',
      '9010',
      9011,
    ]
    const raw = [{ themePackId: '1001', difficulty: 0, giftIds }]
    for (const stage of ruleStages) {
      const admission = admitFloors(parseFloors(raw, FLOOR_RULE_TABLE.categories['5F']), stage)
      expect(admission.ok ? [] : pairsOf(admission.violations)).toEqual([
        ['INVALID_FIELD_TYPE', 'floorSelections[0].giftIds[2]'],
        ['INVALID_FIELD_TYPE', 'floorSelections[0].giftIds[10]'],
      ])
    }
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
      const expected = c.expect[stage].violations
      const { parsed, admission } = admit(c, stage)
      const reported = [
        ...(admission.ok ? [] : admission.violations),
        ...floorReferenceViolations(parsed, corpusGameData),
      ].sort(byPathThenCode)
      expect({ ok: admission.ok, violations: pairsOf(reported) }).toEqual({
        ok: !expected.some((v) => isModuleCode(v.code)),
        violations: pairs(expected),
      })
    })
  })

  describe('index', () => {
    it.each(moduleCases.map((c) => [c.name, c] as const))('%s', (_name, c) => {
      const { parsed, admission } = admit(c, 'index')
      const admitted = admission.ok ? admission.floors : []
      const boundaryViolations = admission.ok ? admission.boundaryViolations : admission.violations
      expect(admission.ok).toBe(true)
      expect({
        ok: boundaryViolations.length === 0,
        floors: indicesOf(parsed, 'accepted'),
        salvaged: indicesOf(parsed, 'rejected'),
        admitted: admitted.map(outcomeFloor),
        violations: pairsOf(boundaryViolations),
      }).toEqual({
        ok: c.expect.index.ok,
        floors: c.expect.index.floors,
        salvaged: c.expect.index.salvaged,
        admitted: c.expect.index.admitted,
        violations: pairs(c.expect.index.violations),
      })
    })
  })
})
