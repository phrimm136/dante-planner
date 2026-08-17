/**
 * Identity Facet Counts
 *
 * Per-value tallies the identity filter dropdowns show beside each option.
 */

/** The identity spec fields the tallies read. */
interface CountableEntry {
  season: number
  unitKeywordList: readonly string[]
}

export interface IdentityFacetCounts {
  /** Identities per season, keyed by the season number as a string. */
  seasonCounts: Record<string, number>
  /** Identities per unit keyword. */
  unitKeywordCounts: Record<string, number>
}

/** Tallies each dropdown-facet value across the whole identity spec. */
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
