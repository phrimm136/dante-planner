import { useFilterI18nData } from '@/shared/filter'
import { EntityMetaInfo } from './EntityMetaInfo'

interface EntityMetaInfoWithI18nProps {
  season: number
  updateDate: number
}

/**
 * Suspends on the season name list, then renders EntityMetaInfo.
 *
 * Pattern: IdentityHeaderI18n.tsx / EGOHeaderI18n.tsx
 */
export function EntityMetaInfoWithI18n({ season, updateDate }: EntityMetaInfoWithI18nProps) {
  const { seasonsI18n } = useFilterI18nData()
  const seasonName = seasonsI18n[String(season) as `${number}`] || `Season ${season}`

  return <EntityMetaInfo season={season} seasonName={seasonName} updateDate={updateDate} />
}
