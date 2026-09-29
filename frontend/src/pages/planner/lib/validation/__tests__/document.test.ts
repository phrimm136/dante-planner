import { describe, it, expect } from 'vitest'

import { MAX_NOTE_BYTES } from '@/lib/constants'
import { calculateNoteByteLength } from '@/shared/noteEditor'
import { keywordViolations, noteSizeViolations, titleViolations } from '../document'
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
      {
        code: 'UNKNOWN_KEYWORD',
        path: 'selectedKeywords[0]',
        params: { keyword: 'GhostKeyword' },
      },
    ])
  })

  it('reports each unknown string after the rename map, a blank one included', () => {
    expect(rendered(keywordViolations(['AccelBullet', 'Nope', '']))).toEqual([
      { code: 'UNKNOWN_KEYWORD', path: 'selectedKeywords[1]', params: { keyword: 'Nope' } },
      { code: 'UNKNOWN_KEYWORD', path: 'selectedKeywords[2]', params: { keyword: '' } },
    ])
  })
})

describe('noteSizeViolations', () => {
  const noteOf = (text: string) => ({
    content: {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
    },
  })
  const overhead = calculateNoteByteLength(noteOf(''))

  const refusalsOf = (sectionNotes: Record<string, { content: unknown }>) =>
    rendered(noteSizeViolations(sectionNotes))

  it('accepts a note exactly at the byte limit', () => {
    const atLimit = noteOf('a'.repeat(MAX_NOTE_BYTES - overhead))
    expect(calculateNoteByteLength(atLimit)).toBe(MAX_NOTE_BYTES)
    expect(refusalsOf({ floor1: atLimit })).toEqual([])
  })

  it('refuses the first oversized note in plain string key order, one byte over, at its key', () => {
    const overLimit = noteOf('a'.repeat(MAX_NOTE_BYTES - overhead + 1))

    expect(refusalsOf({ intro: noteOf('ok'), floor2: overLimit, floor1: overLimit })).toEqual([
      {
        code: 'SIZE_EXCEEDED',
        path: 'sectionNotes[floor1]',
        params: { section: 'floor1', limit: String(MAX_NOTE_BYTES) },
      },
    ])
  })

  it('orders an integer-like note key as a string, not ahead of the others', () => {
    const overLimit = noteOf('a'.repeat(MAX_NOTE_BYTES - overhead + 1))

    expect(refusalsOf({ floor1: overLimit, '10': overLimit, '9': overLimit })).toMatchObject([
      { path: 'sectionNotes[10]' },
    ])
  })
})
