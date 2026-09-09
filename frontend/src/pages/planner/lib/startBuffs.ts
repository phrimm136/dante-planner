import type { StartBuff, StartBuffDataList, StartBuffI18n } from '@/shared/gameText'
import { BASE_BUFF_IDS } from '@/shared/gameText'

/** Joins the version's buff specs with the active language's names. */
export function toStartBuffs(spec: StartBuffDataList, names: StartBuffI18n): StartBuff[] {
  return Object.entries(spec).map(([id, buff]) => ({
    id,
    baseId: buff.baseId,
    level: buff.level,
    name: names[buff.localizeId] || buff.localizeId,
    cost: buff.cost,
    effects: buff.effects,
    iconSpriteId: buff.uiConfig.iconSpriteId,
  }))
}

export function getBuffById(buffs: StartBuff[] | undefined, id: number): StartBuff | undefined {
  return buffs?.find((b) => b.id === String(id))
}

/** The 10 base buffs (level 1) for initial display */
export function getBaseBuffs(buffs: StartBuff[] | undefined): StartBuff[] {
  return (
    buffs?.filter(
      (b) => BASE_BUFF_IDS.includes(b.baseId as (typeof BASE_BUFF_IDS)[number]) && b.level === 1,
    ) ?? []
  )
}
