import { FLOOR_RULE_TABLE, KEYWORD_RENAME_MAP, PLANNER_KEYWORDS } from '@/shared/gameData'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { calculateNoteByteLength, measureDocBytes } from '@/shared/noteEditor'
import type { JSONContent } from '@tiptap/core'
import { invalidFieldType, isBlank, isRecord, parseList } from './boundary'
import type { InvalidFieldType } from './boundary'
import type { Codes } from './codes'
import { contentPath, extendPath, field, index, key } from './contentPath'
import type { ContentPath } from './contentPath'

const ROOT_PATH = contentPath()
const TITLE_PATH = contentPath(field('title'))
const KEYWORDS_PATH = contentPath(field('selectedKeywords'))
const SECTION_NOTES = 'sectionNotes'
const SECTION_NOTES_PATH = contentPath(field(SECTION_NOTES))

const REQUIRED_FIELDS: readonly string[] = [
  'selectedKeywords',
  'equipment',
  'deploymentOrder',
  'floorSelections',
  SECTION_NOTES,
  'skillEAState',
]

const ALLOWED_FIELDS: ReadonlySet<string> = new Set([
  ...REQUIRED_FIELDS,
  'selectedBuffIds',
  'selectedGiftKeyword',
  'selectedGiftIds',
  'observationGiftIds',
  'comprehensiveGiftIds',
])

const PLANNER_KEYWORD_SET: ReadonlySet<string> = new Set(PLANNER_KEYWORDS)

export type DocumentRefusal =
  | {
      code: Codes<'INVALID_CATEGORY' | 'UNKNOWN_FIELD' | 'MISSING_REQUIRED_FIELD'>
      path: ContentPath
    }
  | {
      code: Codes<'SIZE_EXCEEDED'>
      path: ContentPath
      params: { section: string; limit: string }
    }

export type DocumentViolation =
  | InvalidFieldType
  | { code: Codes<'MISSING_TITLE' | 'UNKNOWN_VERSION'>; path: ContentPath }
  | { code: Codes<'UNKNOWN_KEYWORD'>; path: ContentPath; params: { keyword: string } }

const refusal = (
  code: Codes<'INVALID_CATEGORY' | 'UNKNOWN_FIELD' | 'MISSING_REQUIRED_FIELD'>,
): DocumentRefusal => ({ code, path: ROOT_PATH })

const presentFields = (content: Record<string, unknown>): string[] =>
  Object.keys(content).filter((name) => content[name] !== undefined)

function oversizedNote(sectionNotes: unknown): DocumentRefusal | undefined {
  if (!isRecord(sectionNotes)) return undefined
  const oversized = Object.entries(sectionNotes).find(
    ([, note]) =>
      note !== undefined && calculateNoteByteLength(note as { content: unknown }) > MAX_NOTE_BYTES,
  )
  return oversized === undefined
    ? undefined
    : {
        code: 'SIZE_EXCEEDED',
        path: extendPath(SECTION_NOTES_PATH, key(oversized[0])),
        params: { section: oversized[0], limit: String(MAX_NOTE_BYTES) },
      }
}

export function documentRefusal(content: unknown, category: string): DocumentRefusal | undefined {
  if (!Object.hasOwn(FLOOR_RULE_TABLE.categories, category)) return refusal('INVALID_CATEGORY')
  const fields = isRecord(content) ? content : {}
  const present = new Set(presentFields(fields))
  if ([...present].some((name) => !ALLOWED_FIELDS.has(name))) return refusal('UNKNOWN_FIELD')
  if (REQUIRED_FIELDS.some((name) => !present.has(name))) return refusal('MISSING_REQUIRED_FIELD')
  return oversizedNote(fields[SECTION_NOTES])
}

export const unknownVersion = (): DocumentViolation => ({
  code: 'UNKNOWN_VERSION',
  path: ROOT_PATH,
})

export const titleViolations = (title: string | undefined): DocumentViolation[] =>
  title === undefined || isBlank(title) ? [{ code: 'MISSING_TITLE', path: TITLE_PATH }] : []

export const sectionNotesViolations = (raw: unknown): DocumentViolation[] =>
  raw === undefined || isRecord(raw) ? [] : [invalidFieldType(SECTION_NOTES_PATH)]

type SelectedKeyword = { keyword: string; path: ContentPath }

export type ParsedKeywords = {
  keywords: readonly SelectedKeyword[]
  violations: readonly InvalidFieldType[]
}

const renamedKeyword = (keyword: string): string =>
  (Object.hasOwn(KEYWORD_RENAME_MAP, keyword) ? KEYWORD_RENAME_MAP[keyword] : undefined) ?? keyword

export function parseKeywords(raw: unknown): ParsedKeywords {
  const list = parseList(raw, KEYWORDS_PATH)
  const elements = list.elements.map((value, position) => ({
    value,
    path: extendPath(KEYWORDS_PATH, index(position)),
  }))
  return {
    keywords: elements.flatMap(({ value, path }) =>
      typeof value === 'string' ? [{ keyword: renamedKeyword(value), path }] : [],
    ),
    violations: [
      ...list.violations,
      ...elements.flatMap(({ value, path }) =>
        typeof value === 'string' ? [] : [invalidFieldType(path)],
      ),
    ],
  }
}

export const keywordViolations = (parsed: ParsedKeywords): DocumentViolation[] =>
  parsed.keywords
    .filter(({ keyword }) => !PLANNER_KEYWORD_SET.has(keyword))
    .map(({ keyword, path }) => ({ code: 'UNKNOWN_KEYWORD', path, params: { keyword } }))

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
