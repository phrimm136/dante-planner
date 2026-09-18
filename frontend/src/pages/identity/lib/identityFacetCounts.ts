interface CountableEntry {
  season: number
  unitKeywordList: readonly string[]
}

export interface IdentityFacetCounts {
  seasonCounts: Record<string, number>
  unitKeywordCounts: Record<string, number>
}

export function buildFacetCounts(spec: Record<string, CountableEntry>): IdentityFacetCounts {
  const seasonCounts: Record<string, number> = {}
  const unitKeywordCounts: Record<string, number> = {}

  for (const entry of Object.values(spec)) {
    const season = String(entry.season)
    seasonCounts[season] = (seasonCounts[season] ?? 0) + 1

    for (const unitKeyword of entry.unitKeywordList) {
      unitKeywordCounts[unitKeyword] = (unitKeywordCounts[unitKeyword] ?? 0) + 1
    }
  }

  return { seasonCounts, unitKeywordCounts }
}
