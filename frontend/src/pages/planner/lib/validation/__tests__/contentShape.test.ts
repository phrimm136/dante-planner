import { describe, it, expect } from 'vitest'

import { SINNERS } from '@/shared/gameData'
import { parseContent } from '../contentShape'
import { rendered } from './rendered'

const SINNER_KEYS = SINNERS.map((_, sinnerIndex) => String(sinnerIndex + 1))

const sinnerIdPart = (sinnerKey: string) => sinnerKey.padStart(2, '0')

function storedContent(): Record<string, unknown> & {
  equipment: Record<string, Record<string, unknown>>
  skillEAState: Record<string, Record<string, unknown>>
} {
  return {
    selectedKeywords: [],
    selectedBuffIds: [],
    selectedGiftKeyword: null,
    selectedGiftIds: [],
    observationGiftIds: [],
    comprehensiveGiftIds: [],
    equipment: Object.fromEntries(
      SINNER_KEYS.map((sinnerKey) => [
        sinnerKey,
        {
          identity: { id: `1${sinnerIdPart(sinnerKey)}01`, level: 45, uptie: 4 },
          egos: { ZAYIN: { id: `2${sinnerIdPart(sinnerKey)}01`, threadspin: 4 } },
        },
      ]),
    ),
    deploymentOrder: [],
    skillEAState: Object.fromEntries(
      SINNER_KEYS.map((sinnerKey) => [sinnerKey, { 0: 3, 1: 2, 2: 1 }]),
    ),
    floorSelections: [{ themePackId: '1001', difficulty: 0, giftIds: [] }],
    sectionNotes: {},
  }
}

const violationsOf = (content: unknown) => {
  const parsed = parseContent(content)
  return parsed.ok ? [] : rendered(parsed.error)
}

describe('parseContent', () => {
  it('returns the parsed content of a stored-shape document', () => {
    const parsed = parseContent(storedContent())
    expect(parsed.ok && parsed.value.equipment['12']?.identity.id).toBe('11201')
  })

  it.each([
    [['ZAYIN']],
    [['TETH', 'ZAYIN']],
    [['HE', 'WAW', 'TETH', 'ZAYIN']],
    [['ALEPH', 'HE', 'WAW', 'TETH', 'ZAYIN']],
  ])('admits the stored E.G.O slot family %j', (egoTypes) => {
    const content = storedContent()
    content.equipment['1'] = {
      identity: { id: '10101', level: 1, uptie: 1 },
      egos: Object.fromEntries(
        egoTypes.map((egoType) => [egoType, { id: '20101', threadspin: 1 }]),
      ),
    }
    expect(violationsOf(content)).toEqual([])
  })

  it('maps an absent required field to MISSING_REQUIRED_FIELD and a null one to INVALID_FIELD_TYPE', () => {
    const absent = storedContent()
    delete absent['deploymentOrder']
    expect(violationsOf(absent)).toEqual([
      { code: 'MISSING_REQUIRED_FIELD', path: 'deploymentOrder' },
    ])
    expect(violationsOf({ ...storedContent(), deploymentOrder: null })).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'deploymentOrder' },
    ])
  })

  it('maps a nested absent field at its own path, a sinner key rendered as a key', () => {
    const content = storedContent()
    delete content.equipment['12']
    content.equipment['3'] = { egos: { ZAYIN: { id: '20301', threadspin: 4 } } }
    expect(violationsOf(content)).toEqual([
      { code: 'MISSING_REQUIRED_FIELD', path: 'equipment[12]' },
      { code: 'MISSING_REQUIRED_FIELD', path: 'equipment[3].identity' },
    ])
  })

  it('reports each unknown key at its own path, at any depth', () => {
    const content = storedContent()
    content['mystery'] = 1
    content.equipment['01'] = content.equipment['1'] ?? {}
    content.equipment['2'] = {
      ...content.equipment['2'],
      egos: { ZAYIN: { id: '20201', threadspin: 4 }, KETHER: { id: '20201', threadspin: 4 } },
    }
    content.skillEAState['1'] = { 0: 3, 1: 2, 2: 1, 3: 0 }
    expect(violationsOf(content)).toEqual([
      { code: 'UNKNOWN_FIELD', path: 'equipment[01]' },
      { code: 'UNKNOWN_FIELD', path: 'equipment[2].egos.KETHER' },
      { code: 'UNKNOWN_FIELD', path: 'mystery' },
      { code: 'UNKNOWN_FIELD', path: 'skillEAState[1][3]' },
    ])
  })

  it('maps a malformed id, a blank keyword, a fraction and an unsafe integer to INVALID_FIELD_TYPE', () => {
    const content = storedContent()
    content.equipment['1'] = {
      identity: { id: '10101', level: 2 ** 60, uptie: 4 },
      egos: { ZAYIN: { id: '', threadspin: 4 } },
    }
    content.skillEAState['2'] = { 0: 2.5, 1: 2, 2: 1.5 }
    content['selectedGiftKeyword'] = ' '
    expect(violationsOf(content)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[1].egos.ZAYIN.id' },
      { code: 'INVALID_FIELD_TYPE', path: 'equipment[1].identity.level' },
      { code: 'INVALID_FIELD_TYPE', path: 'selectedGiftKeyword' },
      { code: 'INVALID_FIELD_TYPE', path: 'skillEAState[2][0]' },
      { code: 'INVALID_FIELD_TYPE', path: 'skillEAState[2][2]' },
    ])
  })

  it('renders a section note key as a key and a floor index as an index', () => {
    const content = {
      ...storedContent(),
      sectionNotes: { floor1: {} },
      floorSelections: [{ themePackId: '1001', difficulty: 0, giftIds: [9001] }],
    }
    expect(violationsOf(content)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds[0]' },
      { code: 'MISSING_REQUIRED_FIELD', path: 'sectionNotes[floor1].content' },
    ])
  })

  it('reports a content root of the wrong kind at the root', () => {
    expect(violationsOf([])).toEqual([{ code: 'INVALID_FIELD_TYPE', path: '' }])
  })
})
