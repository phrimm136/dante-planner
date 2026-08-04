/** Shared spec/i18n fixtures for the identity detail page tests. */

/** Identity 10101 — LCB Sinner, the minimal base identity. */
export const identitySpec10101 = {
  updatedDate: 20230227,
  skillKeywordList: ['Sinking'],
  panicType: 9999,
  season: 0,
  rank: 1,
  hp: { defaultStat: 72, incrementByLevel: 2.48 },
  defCorrection: -2,
  minSpeedList: [4, 4, 4, 4],
  maxSpeedList: [6, 7, 8, 8],
  unitKeywordList: ['BASE_APPEARANCE', 'SMALL', 'LIMBUS_COMPANY', 'LIMBUS_COMPANY_LCB'],
  staggerList: [65, 35, 15],
  ResistInfo: { SLASH: 2, PENETRATE: 0.5, HIT: 1 },
  mentalConditionInfo: {
    add: ['OnWinDuelAsParryingCountMultiply10AndPlus20Percent'],
    min: ['OnDieAllyAsLevelRatio10'],
  },
  skills: {
    skill1: [{ id: 1010101, skillData: [{ attributeType: 'AZURE', atkType: 'SLASH' }] }],
    skill2: [{ id: 1010102, skillData: [{ attributeType: 'VIOLET', atkType: 'PENETRATE' }] }],
    skill3: [
      { id: 1010103, skillData: [{}, {}, { attributeType: 'AMBER', atkType: 'SLASH' }, {}] },
    ],
    skillDef: [{ id: 1010104, skillData: [{ attributeType: 'NEUTRAL', atkType: 'NONE' }] }],
  },
  passives: {
    battlePassiveList: [[1010101], [], [], []],
    supportPassiveList: [[], [], [1010121], []],
    conditions: {
      '1010101': { type: 'RESONANCE', values: { AZURE: 4 } },
      '1010121': { type: 'STOCK', values: { AZURE: 4 } },
    },
  },
}

export const identityI18n10101 = {
  name: 'LCB\nSinner',
  skills: {
    '1010101': { name: 'Deflect', descs: [{ desc: '', coinDescs: ['Inflict 1 Sinking'] }] },
    '1010102': { name: 'End-stop Stab', descs: [{ desc: '', coinDescs: [] }] },
    '1010103': { name: 'Enjamb', descs: [{ desc: '', coinDescs: [] }] },
    '1010104': { name: 'Guard', descs: [{ desc: '', coinDescs: [] }] },
  },
  passives: {
    '1010101': { name: 'Information Relay', desc: 'Apply 1 Damage Up to 2 allies' },
    '1010121': { name: 'Information Neutralization', desc: 'Heal 10 SP for 1 ally' },
  },
}

/** Identity 10114 — Heishou Pack, passives spread across every uptie. */
export const identitySpec10114 = {
  updatedDate: 20250828,
  skillKeywordList: ['Burst', 'Vibration'],
  panicType: 9999,
  season: 6,
  rank: 3,
  hp: { defaultStat: 66, incrementByLevel: 3.41 },
  defCorrection: 5,
  minSpeedList: [3, 3, 4, 4],
  maxSpeedList: [5, 6, 7, 7],
  unitKeywordList: [
    'SMALL',
    'BLACK_BEAST',
    'BLACK_BEAST_CHIEF',
    'FAMILY_GA',
    'BLACK_BEAST_HORSE',
    'H_CORP',
  ],
  staggerList: [60, 30],
  ResistInfo: { SLASH: 0.5, PENETRATE: 2, HIT: 1 },
  mentalConditionInfo: {
    add: ['OnWinDuelAsParryingCountMultiply10AndPlus20Percent'],
    min: ['OnDieAllyAsLevelRatio10'],
  },
  skills: {
    skill1: [{ id: 1011401, skillData: [{ attributeType: 'AMBER', atkType: 'SLASH' }] }],
    skill2: [{ id: 1011402, skillData: [{ attributeType: 'VIOLET', atkType: 'SLASH' }] }],
    skill3: [
      { id: 1011403, skillData: [{}, {}, { attributeType: 'SHAMROCK', atkType: 'SLASH' }, {}] },
    ],
    skillDef: [{ id: 1011404, skillData: [{ attributeType: 'NEUTRAL', atkType: 'NONE' }] }],
  },
  passives: {
    battlePassiveList: [
      [1011402, 1011403],
      [1011402, 1011403, 1011401],
      [],
      [1011402, 1011403, 1011411],
    ],
    supportPassiveList: [[], [], [1011421], []],
    conditions: {
      '1011401': { type: 'STOCK', values: { SHAMROCK: 5 } },
      '1011421': { type: 'STOCK', values: { SHAMROCK: 4 } },
    },
  },
}

export const identityI18n10114 = {
  name: 'Heishou Pack -\nWu Branch Adept',
  skills: {
    '1011401': { name: 'Cut Down and Trample', descs: [{ desc: 'Test skill', coinDescs: [] }] },
    '1011402': { name: 'Crescent Blade Strike', descs: [{ desc: 'Test skill 2', coinDescs: [] }] },
    '1011403': {
      name: "Cavalry's Vanguard Charge",
      descs: [{ desc: 'Test skill 3', coinDescs: [] }],
    },
    '1011404': {
      name: 'Preparation Afore the Charge',
      descs: [{ desc: 'Defense skill', coinDescs: [] }],
    },
  },
  passives: {
    '1011402': { name: 'Passive 1', desc: 'Passive description 1' },
    '1011403': { name: 'Passive 2', desc: 'Passive description 2' },
    '1011411': { name: 'Passive 3', desc: 'Passive description 3' },
    '1011421': { name: 'Support Passive', desc: 'Support passive description' },
    '1011401': { name: 'Battle Passive', desc: 'Battle passive description' },
  },
}

export const identityTraitLabels = {
  LIMBUS_COMPANY: 'Limbus Company',
  LIMBUS_COMPANY_LCB: 'LCB',
  BLACK_BEAST: 'Black Beast',
  BLACK_BEAST_CHIEF: 'Black Beast Chief',
  FAMILY_GA: 'Family Ga',
  BLACK_BEAST_HORSE: 'Black Beast Horse',
  H_CORP: 'H Corp',
}

export const identityTranslations: Record<string, string> = {
  'skill.skill1': 'Skill 1',
  'skill.skill2': 'Skill 2',
  'skill.skill3': 'Skill 3',
  'skill.defense': 'Defense',
  'passive.battle': 'Battle Passives',
  'passive.support': 'Support Passives',
  'passive.resonance': 'Resonance',
  'passive.stock': 'Stock',
  'sanity.title': 'Sanity',
  'sanity.panicType': 'Panic Type',
  'sanity.panicEffect': 'Panic Effect',
  'sanity.increaseHeader': 'Factors increasing Sanity',
  'sanity.decreaseHeader': 'Factors decreasing Sanity',
  'identity.unitKeyword': 'Unit Keywords',
}
