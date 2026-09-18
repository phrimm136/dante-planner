import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'
import { I18N_LOCALE_MAP } from '@/lib/constants'
import { formatEntityReleaseDate } from '@/lib/formatDate'

const MAX_RECENT_ITEMS = 16

const MAX_DATE_GROUPS = 4

import type { EgoType } from '@/shared/gameData'
import { typedEntries } from '@/lib/utils'
import type { EGOId, IdentityId } from '@/shared/gameData'

export interface RecentIdentityData {
  id: IdentityId
  updateDate: number
  rank: number
  season: number
}

export interface RecentEGOData {
  id: EGOId
  updateDate: number
  egoType: EgoType
  season: number
}

export type RecentEntity =
  | { type: 'identity'; data: RecentIdentityData }
  | { type: 'ego'; data: RecentEGOData }

export interface DateGroup {
  date: number
  formattedDate: string
  entities: RecentEntity[]
}

function groupEntitiesByDate(entities: RecentEntity[], language: string): DateGroup[] {
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

  const sortedDates = [...groupMap.keys()].sort((a, b) => b - a)

  const limitedDates = sortedDates.slice(0, MAX_DATE_GROUPS)

  return limitedDates.map((date) => ({
    date,
    formattedDate: formatEntityReleaseDate(date, I18N_LOCALE_MAP[language] ?? 'en-US'),
    entities: groupMap.get(date) ?? [],
  }))
}

export function useRecentlyReleasedData(language: string) {
  const identitySpecs = useIdentityListSpec()
  const egoSpecs = useEGOListSpec()

  const dateGroups = (() => {
    const identities: RecentEntity[] = typedEntries(identitySpecs).map(([id, spec]) => ({
      type: 'identity' as const,
      data: { id, updateDate: spec.updateDate, rank: spec.rank, season: spec.season },
    }))
    const egos: RecentEntity[] = typedEntries(egoSpecs).map(([id, spec]) => ({
      type: 'ego' as const,
      data: { id, updateDate: spec.updateDate, egoType: spec.egoType, season: spec.season },
    }))

    const combined = [...identities, ...egos].sort((a, b) => b.data.updateDate - a.data.updateDate)

    const limited = combined.slice(0, MAX_RECENT_ITEMS)

    return groupEntitiesByDate(limited, language)
  })()

  return { dateGroups }
}
