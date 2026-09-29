import { PLANNER_KEYWORDS, migrateKeywords } from '@/shared/gameData'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { measureDocBytes } from '@/shared/noteEditor'
import type { JSONContent } from '@tiptap/core'
import { isBlank, parseList } from './boundary'
import type { InvalidFieldType } from './boundary'
import type { Codes } from './codes'
import { contentPath, field } from './contentPath'
import type { ContentPath } from './contentPath'

const TITLE_PATH = contentPath(field('title'))
const KEYWORDS_PATH = contentPath(field('selectedKeywords'))

const PLANNER_KEYWORD_SET: ReadonlySet<string> = new Set(PLANNER_KEYWORDS)

export type DocumentViolation =
  | InvalidFieldType
  | { code: Codes<'MISSING_TITLE'>; path: ContentPath }
  | { code: Codes<'UNKNOWN_KEYWORD'>; path: ContentPath; params: { keyword: string } }

export const titleViolations = (title: string | undefined): DocumentViolation[] =>
  title === undefined || isBlank(title) ? [{ code: 'MISSING_TITLE', path: TITLE_PATH }] : []

export function keywordViolations(raw: unknown): DocumentViolation[] {
  const { elements, violations } = parseList(raw, KEYWORDS_PATH)
  const unknown = migrateKeywords(elements)
    .filter((keyword) => !PLANNER_KEYWORD_SET.has(keyword))
    .map(
      (keyword): DocumentViolation => ({
        code: 'UNKNOWN_KEYWORD',
        path: KEYWORDS_PATH,
        params: { keyword },
      }),
    )
  return [...violations, ...unknown]
}

export type NoteSizeError = { key: string; params: Record<string, string> }

const NOTE_TOO_LARGE = 'pages.plannerMD.validation.noteTooLarge'

export function validateNoteSizes(
  sectionNotes: Record<string, { content: unknown }>,
): NoteSizeError | null {
  const oversized = Object.entries(sectionNotes ?? {}).find(
    ([, note]) => measureDocBytes(note.content as JSONContent) > MAX_NOTE_BYTES,
  )
  return oversized === undefined
    ? null
    : { key: NOTE_TOO_LARGE, params: { section: oversized[0], limit: String(MAX_NOTE_BYTES) } }
}
