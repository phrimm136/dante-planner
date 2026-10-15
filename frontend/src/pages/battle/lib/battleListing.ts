import type { BattleNameEntry, BattleNameList, BattleSortKeys } from '../schemas/BattleSpecSchemas'
import type { EnemyEntity } from '../types/EnemyTypes'
import type { AssistantEntity } from '../types/AssistantTypes'
import type { SinnerEventEntity } from '../types/SinnerEventTypes'
import type { BattleListItem } from './battleFilter'
import {
  BATTLE_FAMILIES,
  ENVY_TITLE_SEPARATOR,
  type BattleCase,
  type BattleFamily,
} from './battleVocabulary'
import { chapterLabel, type Translate } from './enemy/enemyFacetOptions'

export type BattleCardItem = BattleListItem<EnemyEntity | AssistantEntity | SinnerEventEntity>

interface SortableItem {
  family: BattleCardItem['family']
  entity: { id: string; sortKey: BattleSortKeys }
}

export function battleDisplayName(
  entry: BattleNameEntry | undefined,
  mode: BattleCase,
  id: string,
): string {
  return entry?.byCase?.[mode] ?? entry?.name ?? id
}

export function cardPersonalityId(item: BattleCardItem): number | undefined {
  return item.family === 'enemy' && 'personalityId' in item.entity
    ? item.entity.personalityId
    : undefined
}

export function envyIdentityTitle(
  personalityId: number | undefined,
  identityTitles: Readonly<Record<string, string>>,
): string | undefined {
  return personalityId === undefined ? undefined : identityTitles[String(personalityId)]
}

export function singleLineTitle(title: string): string {
  return title.replace(/\r?\n/g, ' ')
}

export function battleListName(
  entry: BattleNameEntry | undefined,
  mode: BattleCase,
  id: string,
  identityTitle: string | undefined,
): string {
  const name = battleDisplayName(entry, mode, id)
  if (identityTitle === undefined) return name
  return `${name}${ENVY_TITLE_SEPARATOR}${singleLineTitle(identityTitle)}`
}

export function battleEventTitle(
  family: BattleFamily,
  chapters: readonly string[],
  chapterNames: Readonly<Record<string, string>>,
  t: Translate,
): string | undefined {
  if (family !== 'sinnerEvent' || chapters.length === 0) return undefined
  return chapters.map((key) => chapterLabel(key, chapterNames, t)).join(', ')
}

export function battleCardName(name: string, eventTitle: string | undefined): string {
  return eventTitle === undefined ? name : `${name} (${eventTitle})`
}

export interface EnvyHeaderNames {
  name: string
  abnormalityLine: string
}

export function envyHeaderNames(unitName: string, identityTitle: string): EnvyHeaderNames {
  return { name: identityTitle, abnormalityLine: unitName }
}

export function buildBattleSearchTerms(
  id: string,
  names: BattleNameList,
  mode: BattleCase,
  identityTitle: string | undefined,
  eventTitle: string | undefined,
): string[] {
  const entry = names[id]
  if (entry === undefined) return ['']
  const terms = [
    battleCardName(battleListName(entry, mode, id, identityTitle), eventTitle),
    entry.name,
    ...entry.aliases,
  ]
  return [...new Set(terms.map((term) => term.toLowerCase()))]
}

function compareSortKeys(a: readonly number[], b: readonly number[]): number {
  const length = Math.min(a.length, b.length)
  for (let index = 0; index < length; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0)
    if (difference !== 0) return difference
  }
  return a.length - b.length
}

export function modeItems<TItem extends SortableItem>(
  items: readonly TItem[],
  mode: BattleCase,
): TItem[] {
  return items
    .filter((item) => item.entity.sortKey[mode] !== undefined)
    .sort(
      (a, b) =>
        compareSortKeys(a.entity.sortKey[mode] ?? [], b.entity.sortKey[mode] ?? []) ||
        BATTLE_FAMILIES.indexOf(a.family) - BATTLE_FAMILIES.indexOf(b.family) ||
        Number(a.entity.id) - Number(b.entity.id),
    )
}
