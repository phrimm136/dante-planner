import { describe, it, expect } from 'vitest'

import assistant400041 from '@static/data/assistant/400041.json'
import assistant400041EN from '@static/i18n/EN/assistant/400041.json'
import assistant400041KR from '@static/i18n/KR/assistant/400041.json'
import {
  AssistantDataSchema,
  BattleEntityI18nSchema,
  type BattleKit,
  type BattlePhase,
  type EngagementRow,
} from '../../schemas/BattleDetailSchemas'
import {
  activePhase,
  defaultFormKey,
  formsLocked,
  rowFormKeys,
  selectedFormKey,
} from '../battleForms'
import { BATTLE_FORMS } from '../battleVocabulary'

const pwie = AssistantDataSchema.parse(assistant400041)

function kitOf(unitId: string): BattleKit {
  const kit = pwie.units[unitId]
  if (kit === undefined) throw new Error(`no unit ${unitId}`)
  return kit
}

function rowOn(stageId: string): EngagementRow {
  const row = pwie.engagements.find((each) => each.stage.endsWith(`#${stageId}`))
  if (row === undefined) throw new Error(`no row on ${stageId}`)
  return row
}

function skillLevels(phase: BattlePhase): Record<string, number> {
  return Object.fromEntries(
    Object.entries(phase.skills).map(([id, entry]) => [
      id,
      Math.max(...entry.levels.map((level) => level.level)),
    ]),
  )
}

describe('Pwie rows', () => {
  it.each(['11051', '11042', '11028'])('locks %s on the base form', (stageId) => {
    const row = rowOn(stageId)
    expect(rowFormKeys(row)).toEqual(['base'])
    expect(formsLocked(row)).toBe(true)
    expect(defaultFormKey(row)).toBe('base')
  })

  it('locks 11030 on shoes and 11038 on cape', () => {
    expect(rowFormKeys(rowOn('11030'))).toEqual(['shoes'])
    expect(rowFormKeys(rowOn('11038'))).toEqual(['cape'])
    expect(formsLocked(rowOn('11030'))).toBe(true)
  })

  it.each([
    ['11044', ['base', 'shoes', 'cape']],
    ['11047', ['base', 'shoes', 'cape']],
    ['11046', ['base', 'shoes']],
    ['11048', ['base', 'shoes']],
    ['11052', ['shoes', 'cape']],
    ['11034', ['shoes', 'cape']],
  ])('enables the forms %s can be fought in', (stageId, forms) => {
    expect(rowFormKeys(rowOn(stageId))).toEqual(forms)
    expect(formsLocked(rowOn(stageId))).toBe(false)
  })

  it('keeps one row each on 11033 and 11038', () => {
    const stages = pwie.engagements.map((row) => row.stage)
    expect(stages.filter((stage) => stage.endsWith('#11033'))).toHaveLength(1)
    expect(stages.filter((stage) => stage.endsWith('#11038'))).toHaveLength(1)
  })

  it('holds the cocoon 400042 on 11031 with no form data', () => {
    const row = rowOn('11031')
    expect(row.unit).toBe(400042)
    expect(rowFormKeys(row)).toEqual([])
    expect(defaultFormKey(row)).toBeUndefined()
    const cocoon = kitOf('400042')
    expect(activePhase(cocoon, row, 0, undefined)).toBe(cocoon.phases[0])
    expect(cocoon.level).toBe(1)
  })

  it('ships no Pwie row on 11026, 11027 and no 400041 row on 11031', () => {
    const units = (stageId: string) =>
      pwie.engagements.filter((row) => row.stage.endsWith(`#${stageId}`)).map((row) => row.unit)
    expect(units('11026')).toEqual([])
    expect(units('11027')).toEqual([])
    expect(units('11031')).toEqual([400042])
  })
})

describe('Pwie form phases', () => {
  const kit = kitOf('400041')
  const optional = rowOn('11044')

  it('activates the form phase of the selected form', () => {
    const shoes = activePhase(kit, optional, 0, 'shoes')
    const cape = activePhase(kit, optional, 0, 'cape')
    expect(shoes.sprite?.file).toBe('400041_LittleBuffe_2pAppearance.webp')
    expect(cape.sprite?.file).toBe('400041_LittleBuffe_3pAppearance.webp')
  })

  it('starts the base form at level 1 on every skill', () => {
    const base = activePhase(kit, optional, 0, 'base')
    expect(new Set(Object.values(skillLevels(base)))).toEqual(new Set([1]))
    expect(base.startBuffs).toEqual([])
  })

  it('carries skill 40004103 at level 4, Charge 3 and BuffetShoes in shoes', () => {
    const shoes = activePhase(kit, optional, 0, 'shoes')
    expect(skillLevels(shoes)['40004103']).toBe(4)
    expect(shoes.startBuffs).toEqual([
      { keywordId: 'Charge', stack: 3 },
      { keywordId: 'BuffetShoes' },
    ])
  })

  it('carries skill 40004105 at level 2 and BuffetGoldenClothes in cape', () => {
    const cape = activePhase(kit, optional, 0, 'cape')
    expect(skillLevels(cape)['40004105']).toBe(2)
    expect(cape.startBuffs?.map((buff) => buff.keywordId)).toContain('BuffetGoldenClothes')
  })

  it('falls back to the row default when the selected form is not allowed', () => {
    const fixed = rowOn('11030')
    expect(selectedFormKey(fixed, 'cape')).toBe('shoes')
    expect(activePhase(kit, fixed, 0, 'cape').sprite?.file).toBe(
      '400041_LittleBuffe_2pAppearance.webp',
    )
  })
})

describe('Pwie form names', () => {
  it.each([
    [assistant400041KR, ['기본', '분홍색 메리 제인', '황금 수의']],
    [assistant400041EN, ['Base', 'Pink Mary Janes', 'Golden Shroud']],
  ])('names the three forms', (raw, names) => {
    const formNames =
      BattleEntityI18nSchema.parse(raw).units['400041']?.phases?.['400041']?.formNames
    expect(BATTLE_FORMS.map((key) => formNames?.[key])).toEqual(names)
  })
})
