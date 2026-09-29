import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import type { MDCategory } from '@/shared/gameData'
import type { EGOGiftSpec } from '@/pages/egoGift'
import type { PlannerIdRegistry } from '../plannerValidation'
import { draftValidation, publishValidation } from './stagedValidation'
import type { StagedResult } from './stagedValidation'
import type { MDPlannerContent } from '../../../types/PlannerTypes'

type CorpusViolation = { code: string; path: string }

type CorpusCase = {
  name: string
  category: MDCategory
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
  '../../../../../..',
)
const CORPUS_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-floor-rules.corpus.json')
const corpus = JSON.parse(fs.readFileSync(CORPUS_PATH, 'utf-8')) as Corpus

const TITLE = 'Corpus'
const EGO_MAX_THREADSPIN = 5

const registry: PlannerIdRegistry = {
  identityIds: new Set(corpus.gameData.identityIds),
  egoIds: new Set(corpus.gameData.egoIds),
  egoMaxThreadspin: new Map(corpus.gameData.egoIds.map((egoId) => [egoId, EGO_MAX_THREADSPIN])),
  themePackIds: new Set(corpus.gameData.themePackIds),
  season: { startBuffIds: new Set(corpus.gameData.startBuffIds), startGiftPools: new Map() },
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

const codeSet = (codes: readonly string[]) => [...new Set(codes)].sort()

const contentOf = (c: CorpusCase) =>
  ({ ...corpus.base, floorSelections: c.floorSelections }) as unknown as MDPlannerContent

const validators: Record<
  'draft' | 'publish',
  (content: MDPlannerContent, category: MDCategory) => StagedResult
> = {
  draft: (content, category) =>
    draftValidation(content, category, egoGiftSpec, undefined, registry),
  publish: (content, category) =>
    publishValidation(TITLE, content, category, egoGiftSpec, undefined, registry),
}

describe('planner validators replay the floor-rule corpus', () => {
  describe.each(['draft', 'publish'] as const)('%s', (stage) => {
    it.each(corpus.cases.map((c) => [c.name, c] as const))('%s', (_name, c) => {
      const result = validators[stage](contentOf(c), c.category)
      expect(codeSet(result.errors.map((e) => e.code))).toEqual(
        codeSet(c.expect[stage].violations.map((v) => v.code)),
      )
    })
  })
})
