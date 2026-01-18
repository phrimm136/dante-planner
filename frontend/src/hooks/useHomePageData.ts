import { useMemo } from 'react'

import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import { useEGOListSpec } from '@/hooks/useEGOListData'
import { I18N_LOCALE_MAP } from '@/lib/constants'
import { formatEntityReleaseDate } from '@/lib/formatDate'

/**
 * Maximum number of items to show in recently released section.
 * Designed to fill ~3 date groups with ~5 items each while fitting
 * comfortably in the home page layout without excessive scrolling.
 */
const MAX_RECENT_ITEMS = 16

/**
 * Maximum number of date groups to show.
 * Shows recent releases from the last 3 update dates, typically
 * covering 1-2 weeks of content releases.
 */
const MAX_DATE_GROUPS = 4

import type { EGOType } from '@/types/EGOTypes'

/** Identity data needed for card display */
export interface RecentIdentityData {
  id: string
  updateDate: number
  rank: number
  season: number
}

/** EGO data needed for card display */
export interface RecentEGOData {
  id: string
  updateDate: number
  egoType: EGOType
  season: number
}

/** Union type for mixed entity display */
export type RecentEntity =
  | { type: 'identity'; data: RecentIdentityData }
  | { type: 'ego'; data: RecentEGOData }

/** Date group with entities */
export interface DateGroup {
  date: number
  formattedDate: string
  entities: RecentEntity[]
}

/**
 * Group entities by updateDate and limit to MAX_DATE_GROUPS
 */
function groupEntitiesByDate(
  entities: RecentEntity[],
  language: string
): DateGroup[] {
  const groupMap = new Map<number, RecentEntity[]>()

  for (const entity of entities) {
    const updateDate = entity.data.updateDate
    const existing = groupMap.get(updateDate)
    if (existing) {
      existing.push(entity)
    } else {
      groupMap.set(updateDate, [entity])
    }
  }

  // Sort dates descending (newest first)
  const sortedDates = [...groupMap.keys()].sort((a, b) => b - a)

  // Take only MAX_DATE_GROUPS
  const limitedDates = sortedDates.slice(0, MAX_DATE_GROUPS)

  return limitedDates.map((date) => ({
    date,
    formattedDate: formatEntityReleaseDate(date, I18N_LOCALE_MAP[language] ?? 'en-US'),
    entities: groupMap.get(date) ?? [],
  }))
}

/**
 * Hook that loads recently released Identity and EGO entities
 * Returns entities grouped by release date for the home page
 *
 * Uses spec-only hooks (no i18n) - names loaded via IdentityName/EGOName components
 * Suspends on initial load - wrap in Suspense boundary
 *
 * @param language - Current i18n language code for date formatting
 */
export function useRecentlyReleasedData(language: string) {
  const identitySpecs = useIdentityListSpec()
  const egoSpecs = useEGOListSpec()

  const dateGroups = useMemo(() => {
    // Convert to RecentEntity array with id from record key
    const identities: RecentEntity[] = Object.entries(identitySpecs).map(
      ([id, spec]) => ({
        type: 'identity' as const,
        data: { id, updateDate: spec.updateDate, rank: spec.rank, season: spec.season },
      })
    )
    const egos: RecentEntity[] = Object.entries(egoSpecs).map(([id, spec]) => ({
      type: 'ego' as const,
      data: { id, updateDate: spec.updateDate, egoType: spec.egoType, season: spec.season },
    }))

    // Combine and sort by updateDate descending
    const combined = [...identities, ...egos].sort(
      (a, b) => b.data.updateDate - a.data.updateDate
    )

    // Limit total items
    const limited = combined.slice(0, MAX_RECENT_ITEMS)

    // Group by date
    return groupEntitiesByDate(limited, language)
  }, [identitySpecs, egoSpecs, language])

  return { dateGroups }
}
