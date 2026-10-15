import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { z } from 'zod'

import { AssistantSpecListSchema } from '../AssistantSchemas'
import {
  AssistantDataSchema,
  BattleEntityI18nSchema,
  BattleStageListSchema,
  BattleStageTextsSchema,
  EnemyDataSchema,
  SinnerEventDataSchema,
} from '../BattleDetailSchemas'
import { BattleNameListSchema } from '../BattleSpecSchemas'
import { EnemySpecListSchema } from '../EnemySchemas'
import { SinnerEventSpecListSchema } from '../SinnerEventSchemas'

const STATIC_ROOT = resolve(import.meta.dirname, '../../../../../../static')
const LANGUAGES = ['KR', 'EN', 'JP', 'CN'] as const

function readJson(path: string): unknown {
  return JSON.parse(readFileSync(resolve(STATIC_ROOT, path), 'utf8'))
}

function jsonFiles(dir: string): string[] {
  return readdirSync(resolve(STATIC_ROOT, dir))
    .filter((name) => name.endsWith('.json'))
    .sort()
}

function failures(dir: string, schema: z.ZodType): string[] {
  return jsonFiles(dir).flatMap((name) => {
    const result = schema.safeParse(readJson(`${dir}/${name}`))
    return result.success ? [] : [`${dir}/${name}: ${result.error.issues[0]?.message ?? ''}`]
  })
}

describe('battle data census', () => {
  it('parses every battle stage label', () => {
    const stages = BattleStageListSchema.parse(readJson('data/battleStageList.json'))
    const dungeonLabels = Object.values(stages).flatMap((stage) =>
      (stage.labels ?? []).filter((label) => label.kind === 'storyDungeon'),
    )
    expect(dungeonLabels.length).toBeGreaterThan(0)
  })

  it.each([
    ['data/enemy', EnemyDataSchema],
    ['data/assistant', AssistantDataSchema],
    ['data/sinnerEvent', SinnerEventDataSchema],
  ] as const)('parses every file under %s', (dir, schema) => {
    expect(jsonFiles(dir).length).toBeGreaterThan(0)
    expect(failures(dir, schema)).toEqual([])
  })

  it.each([
    ['data/enemySpecList.json', EnemySpecListSchema],
    ['data/assistantSpecList.json', AssistantSpecListSchema],
    ['data/sinnerEventSpecList.json', SinnerEventSpecListSchema],
  ] as const)('parses %s', (path, schema) => {
    expect(schema.safeParse(readJson(path)).success).toBe(true)
  })

  it.each(LANGUAGES)('parses the %s name lists and stage texts', (language) => {
    const results = [
      ...['enemy', 'assistant', 'sinnerEvent'].map((family) =>
        BattleNameListSchema.safeParse(readJson(`i18n/${language}/${family}NameList.json`)),
      ),
      BattleStageTextsSchema.safeParse(readJson(`i18n/${language}/battleStageList.json`)),
    ]
    expect(results.map((result) => result.success)).toEqual([true, true, true, true])
  })

  it.each(LANGUAGES)('parses every %s battle unit text', (language) => {
    expect([
      ...failures(`i18n/${language}/enemy`, BattleEntityI18nSchema),
      ...failures(`i18n/${language}/assistant`, BattleEntityI18nSchema),
      ...failures(`i18n/${language}/sinnerEvent`, BattleEntityI18nSchema),
    ]).toEqual([])
  })

  it.each(LANGUAGES)('names every acquire condition of the %s assistant texts', (language) => {
    const unnamed = jsonFiles('data/assistant').flatMap((name) => {
      const data = AssistantDataSchema.parse(readJson(`data/assistant/${name}`))
      const texts = BattleEntityI18nSchema.parse(readJson(`i18n/${language}/assistant/${name}`))
      return Object.entries(data.units).flatMap(([unitId, kit]) =>
        kit.phases.flatMap((phase) => {
          const names = texts.units[unitId]?.phases?.[String(phase.id)]?.conditionNames ?? {}
          return Object.values(phase.skills)
            .flatMap((skill) => skill.acquire ?? [])
            .flatMap((set) => set.condition)
            .filter(({ condition }) => names[condition] === undefined)
            .map(({ condition }) => `${name} ${unitId}/${String(phase.id)} ${condition}`)
        }),
      )
    })
    expect(unnamed).toEqual([])
  })
})
