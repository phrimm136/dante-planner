import { invalidFieldType, isRecord } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { extendPath, key } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'

const MIN_SINNER = 1
const MAX_SINNER = 12
const SINNER_KEY_DIGITS = 2
const INTEGER_KEY = /^[+-]?\d+$/

export type SinnerEntry = {
  sinnerKey: string
  path: ContentPath
  value: Record<string, unknown>
}

export type SinnerKeyViolation =
  | { code: Codes<'MISSING_SINNER'>; path: ContentPath; missingSinners: readonly string[] }
  | { code: Codes<'OUT_OF_RANGE_SINNER'>; path: ContentPath }
  | InvalidFieldType

export type ParsedSinners = {
  entries: readonly SinnerEntry[]
  violations: readonly SinnerKeyViolation[]
}

const paddedKey = (sinner: number): string => String(sinner).padStart(SINNER_KEY_DIGITS, '0')

const SINNER_KEYS: readonly string[] = Array.from(
  { length: MAX_SINNER - MIN_SINNER + 1 },
  (_, offset) => paddedKey(MIN_SINNER + offset),
)

type KeyReading = { rawKey: string; sinnerKey?: string; violations: SinnerKeyViolation[] }

const inRange = (sinner: number) => sinner >= MIN_SINNER && sinner <= MAX_SINNER

function readKey(rawKey: string, path: ContentPath): KeyReading {
  const keyPath = extendPath(path, key(rawKey))
  if (!INTEGER_KEY.test(rawKey)) return { rawKey, violations: [invalidFieldType(keyPath)] }
  const sinner = Number(rawKey)
  return inRange(sinner)
    ? { rawKey, sinnerKey: paddedKey(sinner), violations: [] }
    : { rawKey, violations: [{ code: 'OUT_OF_RANGE_SINNER', path: keyPath }] }
}

const keepsKey = (chosen: ReadonlyMap<string, string>, sinnerKey: string, rawKey: string) =>
  chosen.has(sinnerKey) && rawKey !== sinnerKey

function rawKeysBySinner(readings: readonly KeyReading[]): ReadonlyMap<string, string> {
  return readings.reduce((chosen, { rawKey, sinnerKey }) => {
    return sinnerKey === undefined || keepsKey(chosen, sinnerKey, rawKey)
      ? chosen
      : chosen.set(sinnerKey, rawKey)
  }, new Map<string, string>())
}

type EntryResult = { entries: SinnerEntry[]; violations: SinnerKeyViolation[] }

function entryOf(value: unknown, sinnerKey: string, path: ContentPath): EntryResult {
  const entryPath = extendPath(path, key(sinnerKey))
  return isRecord(value)
    ? { entries: [{ sinnerKey, path: entryPath, value }], violations: [] }
    : { entries: [], violations: [invalidFieldType(entryPath)] }
}

function missingSinners(
  present: ReadonlyMap<string, string>,
  path: ContentPath,
): SinnerKeyViolation[] {
  const missing = SINNER_KEYS.filter((sinnerKey) => !present.has(sinnerKey))
  return missing.length > 0 ? [{ code: 'MISSING_SINNER', path, missingSinners: missing }] : []
}

function parseSinnerRecord(raw: Record<string, unknown>, path: ContentPath): ParsedSinners {
  const readings = Object.keys(raw).map((rawKey) => readKey(rawKey, path))
  const present = rawKeysBySinner(readings)
  const results = SINNER_KEYS.flatMap((sinnerKey) => {
    const rawKey = present.get(sinnerKey)
    return rawKey === undefined ? [] : [entryOf(raw[rawKey], sinnerKey, path)]
  })
  return {
    entries: results.flatMap((result) => result.entries),
    violations: [
      ...readings.flatMap((reading) => reading.violations),
      ...missingSinners(present, path),
      ...results.flatMap((result) => result.violations),
    ].sort(byPathThenCode),
  }
}

export function parseSinners(raw: unknown, path: ContentPath): ParsedSinners {
  if (raw === undefined) return parseSinnerRecord({}, path)
  return isRecord(raw)
    ? parseSinnerRecord(raw, path)
    : { entries: [], violations: [invalidFieldType(path)] }
}
