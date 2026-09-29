export const BATTLE_FAMILIES = ['enemy', 'assistant', 'sinnerEvent'] as const

export type BattleFamily = (typeof BATTLE_FAMILIES)[number]

export const BATTLE_CASES = [
  'story',
  'luxcavation',
  'mirrorDungeon',
  'refractionRailway',
  'refractral',
] as const

export type BattleCase = (typeof BATTLE_CASES)[number]

export const HIDDEN_BATTLE = 'hidden'

export type HiddenBattle = typeof HIDDEN_BATTLE

export const RERUN_LINE_MIN = 1000

export const EXP_LUXCAVATION = 'exp'

export const NEUTRAL_RESISTANCE = 1

export const ENGAGEMENT_LABEL_SEPARATOR = ' · '

export const BATTLE_DETAIL_ROUTES = {
  enemy: '/battle/enemy/$id',
  assistant: '/battle/assistant/$id',
  sinnerEvent: '/battle/sinnerEvent/$id',
} as const satisfies Record<BattleFamily, string>
