import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { z } from 'zod'

import {
  AssistantDataSchema,
  BattleEntityI18nSchema,
  BattleStageListSchema,
  EnemyDataSchema,
  SinnerEventDataSchema,
} from '../BattleDetailSchemas'

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
