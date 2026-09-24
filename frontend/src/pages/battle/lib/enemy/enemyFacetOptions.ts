import type { EnemySpecList } from '../../types/EnemyTypes'
import { EXP_LUXCAVATION, RERUN_LINE_MIN } from '../battleVocabulary'

export type Translate = (key: string, options: Record<string, string>) => string

type FacetTexts = Readonly<Record<string, string>>

export interface EnemyFacetKeys {
  chapters: string[]
  themePacks: string[]
  events: string[]
  railwayLines: number[]
  luxcavations: string[]
}

const CHAPTER_GROUPS: Readonly<Record<string, number>> = {
  tutorial: 0,
  canto: 1,
  intervallo: 1,
  walpurgis: 2,
  aprilFools: 3,
}

const UNKNOWN_CHAPTER_GROUP = 4

function splitKey(key: string): [kind: string, arg: string] {
  const colon = key.indexOf(':')
  return colon === -1 ? [key, ''] : [key.slice(0, colon), key.slice(colon + 1)]
}

function chapterRank(key: string): [group: number, value: number, arg: string] {
  const [kind, arg] = splitKey(key)
  const value = Number(arg)
  return [CHAPTER_GROUPS[kind] ?? UNKNOWN_CHAPTER_GROUP, Number.isNaN(value) ? 0 : value, arg]
}

export function compareChapterKeys(a: string, b: string): number {
  const [groupA, valueA, argA] = chapterRank(a)
  const [groupB, valueB, argB] = chapterRank(b)
  if (groupA !== groupB) return groupA - groupB
  if (valueA !== valueB) return valueA - valueB
  return argA.localeCompare(argB)
}

function compareLuxcavationKeys(a: string, b: string): number {
  if (a === b) return 0
  if (a === EXP_LUXCAVATION) return -1
  if (b === EXP_LUXCAVATION) return 1
  return Number(splitKey(a)[1]) - Number(splitKey(b)[1])
}

const byNumericId = (a: string, b: string) => Number(a) - Number(b)

export function collectEnemyFacetKeys(spec: EnemySpecList): EnemyFacetKeys {
  const chapters = new Set<string>()
  const themePacks = new Set<string>()
  const events = new Set<string>()
  const railwayLines = new Set<number>()
  const luxcavations = new Set<string>()

  for (const entry of Object.values(spec)) {
    for (const key of entry.chapters) chapters.add(key)
    for (const key of entry.themePacks) themePacks.add(key)
    for (const key of entry.events) events.add(key)
    for (const line of entry.railwayLines) railwayLines.add(line)
    for (const key of entry.luxcavations) luxcavations.add(key)
  }

  return {
    chapters: [...chapters].sort(compareChapterKeys),
    themePacks: [...themePacks].sort(byNumericId),
    events: [...events].sort(byNumericId),
    railwayLines: [...railwayLines].sort((a, b) => a - b),
    luxcavations: [...luxcavations].sort(compareLuxcavationKeys),
  }
}

export function chapterLabel(key: string, names: FacetTexts, t: Translate): string {
  const [kind, arg] = splitKey(key)
  if (kind === 'canto') return t('battle.chapter.canto', { n: arg })
  if (kind === 'intervallo') return t('battle.chapter.intervallo', { number: arg })
  if (kind === 'tutorial') return t('battle.chapter.tutorial', {})
  return names[key] ?? key
}

export function railwayLineLabel(line: number, names: FacetTexts, t: Translate): string {
  const text = names[String(line)] ?? String(line)
  return line >= RERUN_LINE_MIN ? t('battle.rerun', { line: text }) : text
}

export function luxcavationLabel(key: string, names: FacetTexts, t: Translate): string {
  if (key === EXP_LUXCAVATION) return t('battle.luxcavation.exp', {})
  return names[key] ?? key
}
