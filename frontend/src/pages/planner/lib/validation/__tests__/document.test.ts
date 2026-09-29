import { describe, it, expect } from 'vitest'

import { MAX_NOTE_BYTES } from '@/lib/constants'
import { calculateNoteByteLength } from '@/shared/noteEditor'
import {
  documentRefusal,
  keywordViolations,
  parseKeywords,
  sectionNotesViolations,
  titleViolations,
  validateNoteSizes,
} from '../document'
import { rendered } from './rendered'

describe('titleViolations', () => {
  it('passes a title', () => {
    expect(titleViolations('My Plan')).toEqual([])
  })

  it.each([
    ['absent', undefined],
    ['empty', ''],
    ['blank', '   '],
  ])('reports a title that is %s as MISSING_TITLE', (_label, title) => {
    expect(rendered(titleViolations(title))).toEqual([{ code: 'MISSING_TITLE', path: 'title' }])
  })
})

describe('keywordViolations', () => {
  it('accepts current keyword ids and a renamed one through migration', () => {
    expect(keywordViolations(parseKeywords(['9828', 'Combustion']))).toEqual([])
    expect(keywordViolations(parseKeywords(['AccelBullet']))).toEqual([])
  })

  it('reports an unknown keyword with the keyword as the message parameter', () => {
    expect(rendered(keywordViolations(parseKeywords(['GhostKeyword'])))).toEqual([
      {
        code: 'UNKNOWN_KEYWORD',
        path: 'selectedKeywords[0]',
        params: { keyword: 'GhostKeyword' },
      },
    ])
  })

  it('reads an absent list as empty and reports one of the wrong kind', () => {
    expect(parseKeywords(undefined).violations).toEqual([])
    expect(rendered(parseKeywords('9828').violations)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'selectedKeywords' },
    ])
  })
})

describe('validateNoteSizes', () => {
  function noteWithText(text: string) {
    return {
      content: {
        type: 'doc',
        content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
      },
    }
  }

  const overhead = calculateNoteByteLength(noteWithText(''))

  it('returns null when all notes are within the limit', () => {
    expect(
      validateNoteSizes({ intro: noteWithText('hello'), 'floor-0': noteWithText('') }),
    ).toBeNull()
  })

  it('returns null for an empty notes map', () => {
    expect(validateNoteSizes({})).toBeNull()
  })

  it('accepts a note exactly at the byte limit', () => {
    const atLimit = noteWithText('a'.repeat(MAX_NOTE_BYTES - overhead))
    expect(calculateNoteByteLength(atLimit)).toBe(MAX_NOTE_BYTES)
    expect(validateNoteSizes({ intro: atLimit })).toBeNull()
  })

  it('flags the offending section one byte over the limit', () => {
    const overLimit = noteWithText('a'.repeat(MAX_NOTE_BYTES - overhead + 1))
    const result = validateNoteSizes({ intro: noteWithText('ok'), deckBuilder: overLimit })

    expect(result).toEqual({
      key: 'pages.plannerMD.validation.noteTooLarge',
      params: { section: 'deckBuilder', limit: String(MAX_NOTE_BYTES) },
    })
  })

  it('measures multibyte content as UTF-8 bytes', () => {
    const koreanCharCount = Math.ceil((MAX_NOTE_BYTES - overhead) / 3) + 1
    const result = validateNoteSizes({ 'floor-3': noteWithText('가'.repeat(koreanCharCount)) })

    expect(result?.params.section).toBe('floor-3')
  })
})

describe('parseKeywords', () => {
  const mixed = ['AccelBullet', 'Nope', 5, '']

  it('reports each non-string element at its index', () => {
    expect(rendered(parseKeywords(mixed).violations)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'selectedKeywords[2]' },
    ])
  })

  it('reports each unknown string after the rename map, a blank one included', () => {
    expect(rendered(keywordViolations(parseKeywords(mixed)))).toEqual([
      { code: 'UNKNOWN_KEYWORD', path: 'selectedKeywords[1]', params: { keyword: 'Nope' } },
      { code: 'UNKNOWN_KEYWORD', path: 'selectedKeywords[3]', params: { keyword: '' } },
    ])
  })
})

describe('sectionNotesViolations', () => {
  it('accepts an object of notes', () => {
    expect(sectionNotesViolations({})).toEqual([])
  })

  it.each([
    ['null', null],
    ['an array', []],
    ['a string', 'x'],
    ['a number', 7],
  ])('reports notes that are %s as INVALID_FIELD_TYPE', (_label, notes) => {
    expect(rendered(sectionNotesViolations(notes))).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'sectionNotes' },
    ])
  })
})

describe('documentRefusal', () => {
  const noteOf = (text: string) => ({
    content: {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
    },
  })
  const overhead = calculateNoteByteLength(noteOf(''))

  const document = (fields: Record<string, unknown> = {}) => ({
    selectedKeywords: [],
    equipment: {},
    deploymentOrder: [],
    floorSelections: [],
    sectionNotes: {},
    skillEAState: {},
    ...fields,
  })

  const refusalOf = (content: unknown, category = '5F') => {
    const refused = documentRefusal(content, category)
    return refused === undefined ? undefined : rendered([refused])[0]
  }

  it('admits a document with every required field and only known fields', () => {
    expect(refusalOf(document({ selectedBuffIds: [], selectedGiftKeyword: null }))).toBeUndefined()
  })

  it.each(['XX', '', 'toString'])('refuses the category %j outside the rule table', (category) => {
    expect(refusalOf(document(), category)).toEqual({ code: 'INVALID_CATEGORY', path: '' })
  })

  it('refuses an unknown top-level field at the root', () => {
    expect(refusalOf(document({ mystery: 1 }))).toEqual({ code: 'UNKNOWN_FIELD', path: '' })
  })

  it.each([
    'selectedKeywords',
    'equipment',
    'deploymentOrder',
    'floorSelections',
    'sectionNotes',
    'skillEAState',
  ])('refuses a document without %s as a missing required field', (name) => {
    const content: Record<string, unknown> = document()
    delete content[name]
    expect(refusalOf(content)).toEqual({ code: 'MISSING_REQUIRED_FIELD', path: '' })
  })

  it('counts a null required field as present', () => {
    expect(refusalOf(document({ sectionNotes: null }))).toBeUndefined()
  })

  it('checks the category, then unknown fields, then required fields, then note size', () => {
    const oversizedNotes = { floor1: noteOf('a'.repeat(MAX_NOTE_BYTES)) }
    const withoutEquipment = (fields: Record<string, unknown>) => {
      const content: Record<string, unknown> = document({ sectionNotes: oversizedNotes, ...fields })
      delete content['equipment']
      return content
    }

    expect(refusalOf(withoutEquipment({ mystery: 1 }), 'XX')?.code).toBe('INVALID_CATEGORY')
    expect(refusalOf(withoutEquipment({ mystery: 1 }))?.code).toBe('UNKNOWN_FIELD')
    expect(refusalOf(withoutEquipment({}))?.code).toBe('MISSING_REQUIRED_FIELD')
    expect(refusalOf(document({ sectionNotes: oversizedNotes }))?.code).toBe('SIZE_EXCEEDED')
  })

  it('accepts a note exactly at the byte limit', () => {
    const atLimit = noteOf('a'.repeat(MAX_NOTE_BYTES - overhead))
    expect(calculateNoteByteLength(atLimit)).toBe(MAX_NOTE_BYTES)
    expect(refusalOf(document({ sectionNotes: { floor1: atLimit } }))).toBeUndefined()
  })

  it('refuses the first oversized note in key order, one byte over, at its key', () => {
    const overLimit = noteOf('a'.repeat(MAX_NOTE_BYTES - overhead + 1))
    const sectionNotes = { intro: noteOf('ok'), floor2: overLimit, floor1: overLimit }

    expect(refusalOf(document({ sectionNotes }))).toEqual({
      code: 'SIZE_EXCEEDED',
      path: 'sectionNotes[floor2]',
      params: { section: 'floor2', limit: String(MAX_NOTE_BYTES) },
    })
  })
})
