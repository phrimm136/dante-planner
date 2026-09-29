import type { CSSProperties } from 'react'

import type {
  BattleNameEntry,
  BattleNameList,
  BattleSortKeys,
  BattleSprite,
} from '../schemas/BattleSpecSchemas'
import type { EnemyEntity } from '../types/EnemyTypes'
import type { AssistantEntity } from '../types/AssistantTypes'
import type { SinnerEventEntity } from '../types/SinnerEventTypes'
import type { BattleListItem } from './battleFilter'
import { BATTLE_FAMILIES, type BattleCase } from './battleVocabulary'
import { BATTLE_CARD_SPRITE } from './cardLayout'

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

export function buildBattleSearchTerms(
  id: string,
  names: BattleNameList,
  mode: BattleCase,
): string[] {
  const entry = names[id]
  return entry === undefined ? [''] : [battleDisplayName(entry, mode, id).toLowerCase()]
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

export function spritePlacementStyle(sprite: BattleSprite): CSSProperties | undefined {
  if (!('ppu' in sprite)) return undefined
  return {
    position: 'absolute',
    left: '50%',
    top: `${String(BATTLE_CARD_SPRITE.anchorTopFraction * 100)}%`,
    maxWidth: 'none',
    marginLeft: `${String(-sprite.anchorX)}px`,
    marginTop: `${String(-sprite.anchorY)}px`,
    transformOrigin: `${String(sprite.anchorX)}px ${String(sprite.anchorY)}px`,
    transform: `scale(${String(BATTLE_CARD_SPRITE.pxPerUnit / sprite.ppu)})`,
  }
}
