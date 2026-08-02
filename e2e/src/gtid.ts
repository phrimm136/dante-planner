import type { APIResponse } from '@playwright/test'
import { setCookieHeaders, setCookieValue } from './cookies'

export const RYW_COOKIE = 'ryw_gtid'

/** The raw cookie value as it would be echoed back, or null when the response mints none. */
export function rywCookieValue(response: APIResponse): string | null {
  return setCookieValue(setCookieHeaders(response), RYW_COOKIE)
}

/** The GTID set the response minted, decoded out of its base64url cookie encoding. */
export function capturedGtid(response: APIResponse): string | null {
  const value = rywCookieValue(response)
  if (!value) return null

  const decoded = Buffer.from(value, 'base64url').toString('utf8')
  return decoded.length > 0 ? decoded : null
}

export interface GtidSource {
  uuid: string
  intervals: Array<{ start: number; end: number }>
}

/**
 * Parses a MySQL gtid_set — comma-separated sources, each `uuid:n[-m][:n[-m]]…`.
 *
 * @throws if the value is not a gtid_set, which is itself the assertion the caller wants
 */
export function parseGtidSet(raw: string): GtidSource[] {
  return raw.split(',').map((sourceSet) => {
    const [uuid, ...ranges] = sourceSet.split(':')
    if (!uuid || !/^[0-9a-fA-F-]{36}$/.test(uuid) || ranges.length === 0) {
      throw new Error(`not a gtid_set: ${raw}`)
    }
    return {
      uuid,
      intervals: ranges.map((range) => {
        const [start, end] = range.split('-')
        const from = Number(start)
        if (!Number.isInteger(from)) throw new Error(`not a gtid_set: ${raw}`)
        return { start: from, end: end === undefined ? from : Number(end) }
      }),
    }
  })
}

export function transactionCount(sources: GtidSource[]): number {
  return sources.reduce(
    (total, source) =>
      total +
      source.intervals.reduce((count, interval) => count + interval.end - interval.start + 1, 0),
    0,
  )
}

/**
 * Whether the set reaches back to a source's first transaction. The primary's executed set does,
 * having accumulated since the server was provisioned; a request's own GTIDs do not.
 */
export function reachesFirstTransaction(sources: GtidSource[]): boolean {
  return sources.some((source) => source.intervals.some((interval) => interval.start <= 1))
}
