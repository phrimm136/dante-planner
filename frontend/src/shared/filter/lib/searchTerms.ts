/**
 * Search Terms
 *
 * The free-text half of list filtering: the query predicate every slice's `matchesX`
 * closes with, and the keyword reverse-lookup their term builders share.
 */

/**
 * Whether a query matches any of an item's precomputed terms. Terms are lowercased at
 * build time; the query is lowercased here. An empty query matches everything.
 */
export function matchesSearch(query: string, terms: readonly string[]): boolean {
  if (!query) return true

  const lowerQuery = query.toLowerCase()
  return terms.some((term) => term.includes(lowerQuery))
}

/**
 * The natural-language readings whose internal codes an item carries, in map order.
 *
 * @param reverseMap - Lowercased display name to every internal code that renders as it
 * @param carries - Whether the item carries one internal code
 *
 * @example
 * // gift with keyword 'Burst', map { rupture: ['Burst'] }
 * collectKeywordTerms(mappings.keywordToValue, (code) => code === gift.keyword) // ['rupture']
 */
export function collectKeywordTerms(
  reverseMap: ReadonlyMap<string, readonly string[]>,
  carries: (internalCode: string) => boolean,
): string[] {
  const terms: string[] = []

  for (const [naturalLang, internalCodes] of reverseMap) {
    if (internalCodes.some(carries)) terms.push(naturalLang)
  }

  return terms
}
