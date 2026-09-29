import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

import type { MDCategory } from '@/shared/gameData'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { renderContentPath } from '../contentPath'
import type { PlannerIdRegistry } from '../plannerValidation'
import { draftValidation, publishValidation } from './stagedValidation'
import type { StagedResult } from './stagedValidation'
import type { MDPlannerContent } from '../../../types/PlannerTypes'

type Stage = 'draft' | 'publish'

type Edit = { at: (string | number)[]; value?: unknown; remove?: boolean }

type CorpusViolation = { code: string; path: string }

type CorpusCase = {
  name: string
  category: string
  version?: number
  title?: string | null
  stages?: Stage[]
  raw?: string
  edits: Edit[]
  expected: Partial<Record<Stage, CorpusViolation[]>>
}

type Corpus = {
  gameData: {
    contentVersion: number
    identityIds: string[]
    egoIds: string[]
    egoMaxThreadspin: Record<string, number>
    egoGiftIds: string[]
    egoGiftThemePacks: Record<string, string[]>
    themePackIds: string[]
    startBuffIds: string[]
    startGiftPools: Record<string, string[]>
  }
  base: Record<string, unknown>
  cases: CorpusCase[]
}

type Outcome = { violations: CorpusViolation[] } | { threw: string } | { skipped: string }

const DEFAULT_STAGES: Stage[] = ['draft', 'publish']
const DEFAULT_TITLE = 'Corpus'
const OUT = process.env.CONTENT_CORPUS_OUT

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../../..',
)
const CORPUS_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-content.corpus.json')
const corpus = JSON.parse(fs.readFileSync(CORPUS_PATH, 'utf-8')) as Corpus

const { gameData } = corpus

const season: PlannerIdRegistry['season'] = {
  startBuffIds: new Set(gameData.startBuffIds),
  startGiftPools: new Map(
    Object.entries(gameData.startGiftPools).map(([keyword, giftIds]) => [
      keyword,
      new Set(giftIds),
    ]),
  ),
}

const registryFor = (c: CorpusCase): PlannerIdRegistry => ({
  identityIds: new Set(gameData.identityIds),
  egoIds: new Set(gameData.egoIds),
  egoMaxThreadspin: new Map(Object.entries(gameData.egoMaxThreadspin)),
  themePackIds: new Set(gameData.themePackIds),
  season: (c.version ?? gameData.contentVersion) === gameData.contentVersion ? season : undefined,
})

const egoGiftSpec: Record<string, EGOGiftSpec> = Object.fromEntries(
  gameData.egoGiftIds.map((giftId) => [
    giftId,
    {
      tag: ['TIER_1'],
      keyword: null,
      battleKeywordList: [],
      attributeType: '',
      themePack: gameData.egoGiftThemePacks[giftId] ?? [],
      maxEnhancement: 0,
    },
  ]),
)

type Container = Record<string | number, unknown>

function applyEdit(content: Container, { at, value, remove }: Edit): void {
  const parent = at
    .slice(0, -1)
    .reduce<Container>((node, segment) => node[segment] as Container, content)
  const last = at.at(-1) ?? ''
  if (remove && Array.isArray(parent)) parent.splice(Number(last), 1)
  else if (remove) delete parent[last]
  else parent[last] = structuredClone(value)
}

function contentOf(c: CorpusCase): MDPlannerContent {
  const content = structuredClone(corpus.base) as Container
  c.edits.forEach((edit) => applyEdit(content, edit))
  return content as unknown as MDPlannerContent
}

const titleOf = (c: CorpusCase): string | undefined =>
  c.title === undefined ? DEFAULT_TITLE : (c.title ?? undefined)

function validate(c: CorpusCase, stage: Stage): StagedResult {
  const category = c.category as MDCategory
  return stage === 'draft'
    ? draftValidation(contentOf(c), category, egoGiftSpec, undefined, registryFor(c))
    : publishValidation(titleOf(c), contentOf(c), category, egoGiftSpec, undefined, registryFor(c))
}

function outcomeOf(c: CorpusCase, stage: Stage): Outcome {
  if (c.raw !== undefined)
    return { skipped: 'raw content string; the frontend validates parsed content' }
  try {
    const result = validate(c, stage)
    return {
      violations: result.errors.map((error) => ({
        code: error.code,
        path: renderContentPath(error.path),
      })),
    }
  } catch (error) {
    return { threw: error instanceof Error ? `${error.name}: ${error.message}` : String(error) }
  }
}

const results = () =>
  corpus.cases.flatMap((c) =>
    (c.stages ?? DEFAULT_STAGES).map((stage) => ({
      name: c.name,
      stage,
      outcome: outcomeOf(c, stage),
    })),
  )

const expectedResultCount = corpus.cases.reduce(
  (count, c) => count + (c.stages ?? DEFAULT_STAGES).length,
  0,
)

const stagesOf = (c: CorpusCase): Stage[] => c.stages ?? DEFAULT_STAGES

const parsedCases = corpus.cases.filter((c) => c.raw === undefined)
const rawCases = corpus.cases.filter((c) => c.raw !== undefined)

describe('planner content corpus replay', () => {
  it('records an outcome for every case at every stage', () => {
    expect(results()).toHaveLength(expectedResultCount)
  })

  it('holds an expected list for every stage each case runs', () => {
    const lacking = corpus.cases.flatMap((c) =>
      stagesOf(c)
        .filter((stage) => c.expected[stage] === undefined)
        .map((stage) => `${c.name}@${stage}`),
    )
    expect(lacking).toEqual([])
  })

  it('skips the raw content strings instead of comparing them', () => {
    expect(rawCases.map((c) => c.name).sort()).toEqual([
      'document-raw-empty',
      'document-raw-malformed-json',
      'document-raw-root-array',
    ])
    for (const c of rawCases) {
      for (const stage of stagesOf(c)) expect(outcomeOf(c, stage)).toHaveProperty('skipped')
    }
  })

  describe.each(DEFAULT_STAGES)('%s', (stage) => {
    it.each(
      parsedCases.filter((c) => stagesOf(c).includes(stage)).map((c) => [c.name, c] as const),
    )('%s', (_name, c) => {
      expect(outcomeOf(c, stage)).toEqual({ violations: c.expected[stage] })
    })
  })

  it.skipIf(OUT === undefined)('writes the ordered codes and paths', () => {
    fs.writeFileSync(
      OUT ?? '',
      `${JSON.stringify({ producer: 'frontend', results: results() }, null, 2)}\n`,
    )

    const written = JSON.parse(fs.readFileSync(OUT ?? '', 'utf-8')) as { results: unknown[] }
    expect(written.results).toHaveLength(expectedResultCount)
  })
})
