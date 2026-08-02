import type { APIResponse } from '@playwright/test'

export function setCookieHeaders(response: APIResponse): string[] {
  return response
    .headersArray()
    .filter((header) => header.name.toLowerCase() === 'set-cookie')
    .map((header) => header.value)
}

/**
 * The value a response assigns to a cookie, or null when it assigns none. A cleared cookie
 * (empty value, Max-Age=0) reads as null, which is what a caller asking "was one minted" means.
 */
export function setCookieValue(headers: string[], name: string): string | null {
  const assignment = headers.filter((header) => header.startsWith(`${name}=`)).at(-1)
  if (!assignment) return null

  const value = assignment.slice(`${name}=`.length).split(';')[0]!
  return value.length > 0 ? value : null
}

export function cookieHeader(...pairs: Array<string | null | undefined>): string {
  return pairs.filter((pair): pair is string => Boolean(pair)).join('; ')
}
