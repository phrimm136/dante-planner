import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import { MD_CATEGORIES } from '@/shared/gameData'
import type { MDCategory } from '@/shared/gameData'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { validatePlannerForDraftSave, validatePlannerForPublish } from '../plannerValidation'
import type { PlannerIdRegistry, PlannerValidationResult } from '../plannerValidation'
import type { MDPlannerContent } from '../../types/PlannerTypes'

type CorpusViolation = { code: string; path: string }

type CorpusCase = {
  name: string
  category: string
  floorSelections: unknown
  expect: {
    draft: { violations: CorpusViolation[] }
    publish: { violations: CorpusViolation[] }
  }
}

type Corpus = {
  base: Record<string, unknown>
  gameData: {
    themePackIds: string[]
    egoGiftThemePacks: Record<string, string[]>
    identityIds: string[]
    egoIds: string[]
    startBuffIds: string[]
  }
  cases: CorpusCase[]
}

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../..',
)
const CORPUS_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-floor-rules.corpus.json')
const corpus = JSON.parse(fs.readFileSync(CORPUS_PATH, 'utf-8')) as Corpus

const TITLE = 'Corpus'

const registry: PlannerIdRegistry = {
  identityIds: new Set(corpus.gameData.identityIds),
  egoIds: new Set(corpus.gameData.egoIds),
  themePackIds: new Set(corpus.gameData.themePackIds),
  startBuffIds: new Set(corpus.gameData.startBuffIds),
}

const egoGiftSpec: Record<string, EGOGiftSpec> = Object.fromEntries(
  Object.entries(corpus.gameData.egoGiftThemePacks).map(([giftId, themePack]) => [
    giftId,
    {
      tag: ['TIER_1'],
      keyword: null,
      battleKeywordList: [],
      attributeType: '',
      themePack,
      maxEnhancement: 0,
    },
  ]),
)

const isMdCategory = (category: string): category is MDCategory =>
  (MD_CATEGORIES as readonly string[]).includes(category)

const codeSet = (codes: readonly string[]) => [...new Set(codes)].sort()

const contentOf = (c: CorpusCase) =>
  ({ ...corpus.base, floorSelections: c.floorSelections }) as unknown as MDPlannerContent

const validatorCases = corpus.cases.filter((c) => isMdCategory(c.category))

const validators: Record<
  'draft' | 'publish',
  (content: MDPlannerContent, category: MDCategory) => PlannerValidationResult
> = {
  draft: (content, category) =>
    validatePlannerForDraftSave(content, category, egoGiftSpec, undefined, registry),
  publish: (content, category) =>
    validatePlannerForPublish(TITLE, content, category, egoGiftSpec, undefined, registry),
}

describe('planner validators replay the floor-rule corpus', () => {
  it('leaves only the unknown-category case to the caller boundary', () => {
    expect(corpus.cases.filter((c) => !isMdCategory(c.category)).map((c) => c.name)).toEqual([
      'boundary-unknown-category',
    ])
  })

  describe.each(['draft', 'publish'] as const)('%s', (stage) => {
    it.each(validatorCases.map((c) => [c.name, c] as const))('%s', (_name, c) => {
      const result = validators[stage](contentOf(c), c.category as MDCategory)
      expect(codeSet(result.errors.map((e) => e.code))).toEqual(
        codeSet(c.expect[stage].violations.map((v) => v.code)),
      )
    })
  })
})
