import { useTranslation } from 'react-i18next'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getSeasonColor } from '@/shared/gameData'
import { formatEntityReleaseDate } from '@/lib/formatDate'
import { I18N_LOCALE_MAP } from '@/lib/constants'

interface EntityMetaInfoProps {
  season: number
  seasonName: string
  updateDate: number
}

export function EntityMetaInfo({ season, seasonName, updateDate }: EntityMetaInfoProps) {
  const { t, i18n } = useTranslation(['database', 'common'])

  const seasonColor = getSeasonColor(season)
  const locale = I18N_LOCALE_MAP[i18n.language] ?? 'en-US'
  const formattedDate = formatEntityReleaseDate(updateDate, locale)

  return (
    <div className="grid grid-cols-2 gap-2">
      <LabeledPanel title={t('meta.season')}>
        <div
          className="text-xs text-center"
          style={seasonColor ? { color: seasonColor } : undefined}
        >
          {seasonName}
        </div>
      </LabeledPanel>

      <LabeledPanel title={t('meta.releaseDate')}>
        <div className="text-xs text-center tabular-nums">{formattedDate}</div>
      </LabeledPanel>
    </div>
  )
}
