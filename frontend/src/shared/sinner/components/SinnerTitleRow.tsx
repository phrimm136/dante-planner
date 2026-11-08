import { useTranslation } from 'react-i18next'

import { getSinnerIconPath, getSinnerIconRingPath } from '@/shared/assets'
import { Skeleton } from '@/components/ui/skeleton'
import type { Sinner } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { SECTION_STYLES, SINNER_COLOR_FALLBACK, SINNER_COLORS } from '@/lib/constants'

interface SinnerTitleRowProps {
  sinner: Sinner
  name: string
  rank: string | number
  rankIconSrc: string
  ringRank: number
}

export function SinnerTitleRow({ sinner, name, rank, rankIconSrc, ringRank }: SinnerTitleRowProps) {
  const { i18n } = useTranslation()
  const sinnerColor = SINNER_COLORS[sinner] || SINNER_COLOR_FALLBACK
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <div>
      <div className="flex justify-end">
        <img src={rankIconSrc} alt={`${rank} rank`} className="h-6 object-contain" />
      </div>
      <div className="flex items-center gap-3">
        <div className="relative w-12 h-12 flex-shrink-0">
          <img
            src={getSinnerIconRingPath(ringRank)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
          />
          <img
            src={getSinnerIconPath(sinner)}
            alt={sinner}
            className="absolute inset-0 w-full h-full object-contain p-1"
          />
        </div>
        {name ? (
          <h1
            className={SECTION_STYLES.TEXT.pageTitle}
            style={{ color: sinnerColor, ...displayStyle }}
          >
            {name}
          </h1>
        ) : (
          <Skeleton className="h-8 w-48" style={{ backgroundColor: sinnerColor }} />
        )}
      </div>
    </div>
  )
}
