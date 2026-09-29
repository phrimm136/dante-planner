import type { Codes, ValidationCode } from './codes'
import type { ContentPath } from './contentPath'

export type InvalidFieldType = { code: Codes<'INVALID_FIELD_TYPE'>; path: ContentPath }

export const invalidFieldType = (path: ContentPath): InvalidFieldType => ({
  code: 'INVALID_FIELD_TYPE',
  path,
})

export const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

export const isInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value)

export const isBlank = (value: string): boolean => value.trim() === ''

export const isAbsent = (value: unknown): value is null | undefined =>
  value === undefined || value === null

export const isMissingText = (value: unknown): boolean =>
  isAbsent(value) || (typeof value === 'string' && isBlank(value))

export type ParsedList = { elements: readonly unknown[]; violations: readonly InvalidFieldType[] }

export function parseList(raw: unknown, path: ContentPath): ParsedList {
  if (raw === undefined) return { elements: [], violations: [] }
  if (!Array.isArray(raw)) return { elements: [], violations: [invalidFieldType(path)] }
  const elements: unknown[] = raw
  return { elements, violations: [] }
}

export type Bounds = { min: number; max: number }

export type BoundedIntegerViolation<C extends ValidationCode> =
  | InvalidFieldType
  | { code: C; path: ContentPath }

export function boundedIntegerViolations<C extends ValidationCode>(
  value: unknown,
  path: ContentPath,
  bounds: Bounds,
  outOfRange: C,
): BoundedIntegerViolation<C>[] {
  if (value === undefined) return []
  if (!isInteger(value)) return [invalidFieldType(path)]
  return value < bounds.min || value > bounds.max ? [{ code: outOfRange, path }] : []
}
