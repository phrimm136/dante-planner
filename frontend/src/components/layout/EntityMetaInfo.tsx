import { useTranslation } from 'react-i18next'
import { getSeasonColor } from '@/shared/gameData'
import { formatEntityReleaseDate } from '@/lib/formatDate'
import { I18N_LOCALE_MAP } from '@/lib/constants'

interface EntityMetaInfoProps {
  /** Season number (0=Standard, 1-7=Seasons, 8000=Collab, 9101+=Walpurgis) */
  season: number
  /** Localized season name */
  seasonName: string
  /** Release date as YYYYMMDD integer (e.g., 20230227) */
  updateDate: number
}

/**
 * EntityMetaInfo - Displays season and release date metadata
 *
 * Two-column layout showing:
 * - Season name
 * - Release date (formatted based on locale)
 *
 * Pattern: Follows StatusPanel.tsx grid layout
 */
export function EntityMetaInfo({ season, seasonName, updateDate }: EntityMetaInfoProps) {
  const { t, i18n } = useTranslation(['database', 'common'])

  const seasonColor = getSeasonColor(season)
  const locale = I18N_LOCALE_MAP[i18n.language] ?? 'en-US'
  const formattedDate = formatEntityReleaseDate(updateDate, locale)

  return (
    <div className="grid grid-cols-2 gap-2">
      {/* Season Panel */}
      <div className="border rounded p-3 space-y-2">
        <div className="font-semibold text-sm text-center">{t('meta.season')}</div>
        <div
          className="text-xs text-center"
          style={seasonColor ? { color: seasonColor } : undefined}
        >
          {seasonName}
        </div>
      </div>

      {/* Release Date Panel */}
      <div className="border rounded p-3 space-y-2">
        <div className="font-semibold text-sm text-center">{t('meta.releaseDate')}</div>
        <div className="text-xs text-center tabular-nums">{formattedDate}</div>
      </div>
    </div>
  )
}
