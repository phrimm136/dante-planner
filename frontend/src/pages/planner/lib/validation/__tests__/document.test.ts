import { describe, it, expect } from 'vitest'

import { MAX_NOTE_BYTES } from '@/lib/constants'
import { calculateNoteByteLength } from '@/shared/noteEditor'
import { keywordViolations, titleViolations, validateNoteSizes } from '../document'
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
    expect(keywordViolations(['9828', 'Combustion'])).toEqual([])
    expect(keywordViolations(['AccelBullet'])).toEqual([])
  })

  it('reports an unknown keyword with the keyword as the message parameter', () => {
    expect(rendered(keywordViolations(['GhostKeyword']))).toEqual([
      { code: 'UNKNOWN_KEYWORD', path: 'selectedKeywords', params: { keyword: 'GhostKeyword' } },
    ])
  })

  it('reads an absent list as empty and reports one of the wrong kind', () => {
    expect(keywordViolations(undefined)).toEqual([])
    expect(rendered(keywordViolations('9828'))).toEqual([
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
