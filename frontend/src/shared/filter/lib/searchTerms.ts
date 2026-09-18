export function matchesSearch(query: string, terms: readonly string[]): boolean {
  if (!query) return true

  const lowerQuery = query.toLowerCase()
  return terms.some((term) => term.includes(lowerQuery))
}

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
