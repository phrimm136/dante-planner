import { KEYWORD_RENAME_MAP, PLANNER_KEYWORDS } from '@/shared/gameData'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { calculateNoteByteLength } from '@/shared/noteEditor'
import { isBlank } from './boundary'
import type { Codes } from './codes'
import { contentPath, extendPath, field, index, key } from './contentPath'
import type { ContentPath } from './contentPath'
import type { SectionNotesValue } from './contentShape'

const ROOT_PATH = contentPath()
const TITLE_PATH = contentPath(field('title'))
const KEYWORDS_PATH = contentPath(field('selectedKeywords'))
const SECTION_NOTES_PATH = contentPath(field('sectionNotes'))

const PLANNER_KEYWORD_SET: ReadonlySet<string> = new Set(PLANNER_KEYWORDS)

export type DocumentRefusal = {
  code: Codes<'SIZE_EXCEEDED'>
  path: ContentPath
  params: { section: string; limit: string }
}

export type DocumentViolation =
  | { code: Codes<'MISSING_TITLE' | 'UNKNOWN_VERSION'>; path: ContentPath }
  | { code: Codes<'UNKNOWN_KEYWORD'>; path: ContentPath; params: { keyword: string } }

export function noteSizeViolations(sectionNotes: SectionNotesValue): DocumentRefusal[] {
  const oversized = Object.keys(sectionNotes)
    .sort()
    .find((section) => {
      const note = sectionNotes[section]
      return note !== undefined && calculateNoteByteLength(note) > MAX_NOTE_BYTES
    })
  return oversized === undefined
    ? []
    : [
        {
          code: 'SIZE_EXCEEDED',
          path: extendPath(SECTION_NOTES_PATH, key(oversized)),
          params: { section: oversized, limit: String(MAX_NOTE_BYTES) },
        },
      ]
}

export const unknownVersion = (): DocumentViolation => ({
  code: 'UNKNOWN_VERSION',
  path: ROOT_PATH,
})

export const titleViolations = (title: string | undefined): DocumentViolation[] =>
  title === undefined || isBlank(title) ? [{ code: 'MISSING_TITLE', path: TITLE_PATH }] : []

const renamedKeyword = (keyword: string): string =>
  (Object.hasOwn(KEYWORD_RENAME_MAP, keyword) ? KEYWORD_RENAME_MAP[keyword] : undefined) ?? keyword

export const keywordViolations = (keywords: readonly string[]): DocumentViolation[] =>
  keywords
    .map((keyword, position) => ({
      keyword: renamedKeyword(keyword),
      path: extendPath(KEYWORDS_PATH, index(position)),
    }))
    .filter(({ keyword }) => !PLANNER_KEYWORD_SET.has(keyword))
    .map(({ keyword, path }) => ({ code: 'UNKNOWN_KEYWORD', path, params: { keyword } }))
