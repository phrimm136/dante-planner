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

export type SinnerKey = { rawKey: string; sinner: number }

export type SinnerEntry = {
  sinnerKey: string
  path: ContentPath
  value: Record<string, unknown>
}

export type SinnerKeyViolation =
  | { code: Codes<'MISSING_SINNER'>; path: ContentPath; missingSinners: readonly string[] }
  | { code: Codes<'OUT_OF_RANGE_SINNER' | 'DUPLICATE_SINNER'>; path: ContentPath }
  | InvalidFieldType

export type ParsedSinners = {
  entries: readonly SinnerEntry[]
  violations: readonly SinnerKeyViolation[]
}

const paddedKey = (sinner: number): string => String(sinner).padStart(SINNER_KEY_DIGITS, '0')

const SINNERS: readonly number[] = Array.from(
  { length: MAX_SINNER - MIN_SINNER + 1 },
  (_, offset) => MIN_SINNER + offset,
)

type KeyReading = { sinnerKey?: SinnerKey; violations: SinnerKeyViolation[] }

const inRange = (sinner: number) => sinner >= MIN_SINNER && sinner <= MAX_SINNER

function readKey(rawKey: string, path: ContentPath): KeyReading {
  const keyPath = extendPath(path, key(rawKey))
  if (!INTEGER_KEY.test(rawKey)) return { violations: [invalidFieldType(keyPath)] }
  const sinner = Number(rawKey)
  return inRange(sinner)
    ? { sinnerKey: { rawKey, sinner }, violations: [] }
    : { violations: [{ code: 'OUT_OF_RANGE_SINNER', path: keyPath }] }
}

function keysBySinner(readings: readonly KeyReading[]): ReadonlyMap<number, readonly SinnerKey[]> {
  return readings.reduce((bySinner, { sinnerKey }) => {
    if (sinnerKey === undefined) return bySinner
    return bySinner.set(sinnerKey.sinner, [...(bySinner.get(sinnerKey.sinner) ?? []), sinnerKey])
  }, new Map<number, SinnerKey[]>())
}

type EntryResult = { entries: SinnerEntry[]; violations: SinnerKeyViolation[] }

function entryOf(value: unknown, sinner: number, path: ContentPath): EntryResult {
  const sinnerKey = paddedKey(sinner)
  const entryPath = extendPath(path, key(sinnerKey))
  return isRecord(value)
    ? { entries: [{ sinnerKey, path: entryPath, value }], violations: [] }
    : { entries: [], violations: [invalidFieldType(entryPath)] }
}

const duplicateSinner = (sinnerKeys: readonly SinnerKey[], path: ContentPath): EntryResult => ({
  entries: [],
  violations: sinnerKeys.map(({ rawKey }) => ({
    code: 'DUPLICATE_SINNER',
    path: extendPath(path, key(rawKey)),
  })),
})

function missingSinners(
  present: ReadonlyMap<number, readonly SinnerKey[]>,
  path: ContentPath,
): SinnerKeyViolation[] {
  const missing = SINNERS.filter((sinner) => !present.has(sinner)).map(paddedKey)
  return missing.length > 0 ? [{ code: 'MISSING_SINNER', path, missingSinners: missing }] : []
}

function parseSinnerRecord(raw: Record<string, unknown>, path: ContentPath): ParsedSinners {
  const readings = Object.keys(raw).map((rawKey) => readKey(rawKey, path))
  const present = keysBySinner(readings)
  const results = SINNERS.flatMap((sinner) => {
    const sinnerKeys = present.get(sinner) ?? []
    const [only, ...others] = sinnerKeys
    if (only === undefined) return []
    return others.length > 0
      ? [duplicateSinner(sinnerKeys, path)]
      : [entryOf(raw[only.rawKey], sinner, path)]
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
