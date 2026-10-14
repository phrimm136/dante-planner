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

export const DEFAULT_BATTLE_MODE: BattleCase = 'story'

export const SKILL_LEVEL_ADDER_BY_DEF_TYPE: Readonly<Record<string, 'attack' | 'defense'>> = {
  ATTACK: 'attack',
  COUNTER: 'attack',
  GUARD: 'defense',
  EVADE: 'defense',
}

export const HAZARD_CLASSES = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'] as const

export const LEVEL_NUMBER_PLATES = ['UNDEF', 'HYPHEN'] as const

export const HIDDEN_BATTLE = 'hidden'

export type HiddenBattle = typeof HIDDEN_BATTLE

export const RERUN_LINE_MIN = 1000

export const EXP_LUXCAVATION = 'exp'

export const ROMAN_NUMERALS = 'roman'

export const PROLOGUE_CANTO = 0

export const NEUTRAL_RESISTANCE = 1

export const ENGAGEMENT_LABEL_SEPARATOR = ' · '

export const RPG_ROUTE_LETTER_LENGTH = 1

export const RPG_FLOOR_SUFFIX = 'F'

export const ENGAGEMENT_PLACE_JOINER = ' / '

export const BATTLE_DETAIL_ROUTES = {
  enemy: '/battle/enemy/$id',
  assistant: '/battle/assistant/$id',
  sinnerEvent: '/battle/sinnerEvent/$id',
} as const satisfies Record<BattleFamily, string>
