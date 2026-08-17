/** Shared spec/i18n fixtures for the EGO detail page tests, mirroring EGO 20102. */

export const egoSpec20102 = {
  updatedDate: 20230227,
  egoType: 'TETH',
  season: 1,
  attributeResist: {
    CRIMSON: 0.5,
    SCARLET: 1,
    AMBER: 2,
    SHAMROCK: 1,
    AZURE: 2,
    INDIGO: 1,
    VIOLET: 1,
    WHITE: 2,
    BLACK: 2,
  },
  requirements: { CRIMSON: 5, AMBER: 1, SHAMROCK: 1 },
  skills: {
    awaken: [
      {
        id: 2010211,
        skillData: [
          {
            attributeType: 'CRIMSON',
            atkType: 'SLASH',
            targetNum: 3,
            mpUsage: 20,
            skillLevelCorrection: 2,
            defaultValue: 12,
            scale: 8,
            coinString: 'C',
          },
          {},
          { defaultValue: 12, scale: 8 },
          { targetNum: 5, scale: 8 },
          {
            targetNum: 5,
            defaultValue: 14,
            skillLevelCorrection: 3,
            scale: 12,
          },
        ],
      },
    ],
    erosion: [
      {
        id: 2010221,
        skillData: [
          {
            attributeType: 'CRIMSON',
            atkType: 'SLASH',
            targetNum: 3,
            mpUsage: 20,
            skillLevelCorrection: 2,
            defaultValue: 24,
            scale: -12,
            coinString: 'C',
          },
          {},
          { defaultValue: 24, scale: -12 },
          { targetNum: 5, scale: -12 },
          {
            targetNum: 5,
            defaultValue: 26,
            skillLevelCorrection: 3,
            scale: -12,
            coinString: 'U',
          },
        ],
      },
    ],
  },
  maxThreadspin: 5,
  passives: { passiveList: [[], ['2010211'], [], [], ['2010212']] },
  skillKeywordList: ['Combustion'],
  battleKeywordList: ['Combustion', 'MatchesPersonality', 'SuperCoin'],
}

export const egoI18n20102 = {
  name: '4th Match Flame',
  skills: {
    '2010211': {
      name: '4th Match Flame',
      descs: [
        { desc: '', coinDescs: ['Inflict 2 Combustion'] },
        {},
        { desc: '', coinDescs: ['Inflict 4 Combustion'] },
        { desc: '', coinDescs: ['Inflict 4 Combustion'] },
        { desc: 'Awakening tier 5', coinDescs: ['Inflict 6 Combustion'] },
      ],
    },
    '2010221': {
      name: '4th Match Flame',
      descs: [
        { desc: 'Targets randomly', coinDescs: ['Inflict 2 Combustion'] },
        {},
        { desc: 'Targets randomly', coinDescs: ['Inflict 4 Combustion'] },
        { desc: 'Targets randomly', coinDescs: ['Inflict 4 Combustion'] },
        { desc: 'Erosion tier 5', coinDescs: ['Inflict 6 Combustion'] },
      ],
    },
  },
  passives: {
    '2010211': { name: 'Ember', desc: 'On Clash Win, inflict Combustion' },
    '2010212': { name: 'Ember', desc: 'On Use of a Wrath Skill, Clash Power +1' },
  },
}

export const egoTranslations: Record<string, string> = {
  'skill.awakening': 'Awakening',
  'skill.corrosion': 'Corrosion',
  'passive.none': 'No passives',
  'tabs.skills': 'Skills',
  'passive.battle': 'Battle Passives',
  'meta.season': 'Season',
  'meta.releaseDate': 'Release Date',
}
