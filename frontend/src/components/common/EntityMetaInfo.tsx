import { useTranslation } from 'react-i18next'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'
import { getSeasonColor } from '@/lib/colorUtils'

interface EntityMetaInfoProps {
  /** Season number (0=Standard, 1-7=Seasons, 8000=Collab, 9101+=Walpurgis) */
  season: number
  /** Release date as YYYYMMDD integer (e.g., 20230227) */
  updateDate: number
}

/**
 * Format YYYYMMDD integer to localized date string.
 * @param dateInt - Date as YYYYMMDD integer (e.g., 20230227)
 * @param language - Current language code
 * @returns Formatted date string
 */
function formatDate(dateInt: number, language: string): string {
  const dateStr = String(dateInt)
  const year = dateStr.slice(0, 4)
  const month = dateStr.slice(4, 6)
  const day = dateStr.slice(6, 8)

  // Create date object (months are 0-indexed)
  const date = new Date(Number(year), Number(month) - 1, Number(day))

  // Format based on language
  const locale = language === 'KR' ? 'ko-KR'
    : language === 'JP' ? 'ja-JP'
    : language === 'CN' ? 'zh-CN'
    : 'en-US'

  return date.toLocaleDateString(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

/**
 * EntityMetaInfo - Displays season and release date metadata
 *
 * Two-column layout showing:
 * - Season name (from seasons.json i18n)
 * - Release date (formatted based on locale)
 *
 * Uses Suspense internally via useFilterI18nData - wrap in Suspense boundary.
 *
 * Pattern: Follows StatusPanel.tsx grid layout
 */
export function EntityMetaInfo({ season, updateDate }: EntityMetaInfoProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const { seasonsI18n } = useFilterI18nData()

  const seasonName = seasonsI18n[String(season) as `${number}`] || `Season ${season}`
  const seasonColor = getSeasonColor(season)
  const formattedDate = formatDate(updateDate, i18n.language)

  return (
    <div className="grid grid-cols-2 gap-2">
      {/* Season Panel */}
      <div className="border rounded p-3 space-y-2">
        <div className="font-semibold text-sm text-center">{t('meta.season')}</div>
        <div className="text-xs text-center" style={seasonColor ? { color: seasonColor } : undefined}>{seasonName}</div>
      </div>

      {/* Release Date Panel */}
      <div className="border rounded p-3 space-y-2">
        <div className="font-semibold text-sm text-center">{t('meta.releaseDate')}</div>
        <div className="text-xs text-center tabular-nums">{formattedDate}</div>
      </div>
    </div>
  )
}
