import { Link, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Clock } from 'lucide-react'

import { formatCompactDate } from '@/lib/formatDate'
import { getKeywordIconPath } from '@/shared/assets'
import { PLANNER_LIST, SECTION_STYLES } from '@/lib/constants'
import { MdCategoryLabel } from '../MdCategoryLabel'
import { PlannerStatusIcon } from './PlannerStatusIcon'
import { categoryBadgeStyle, deriveSaveStatus } from '../../lib/plannerBadges'

import type { PlannerSummary } from '../../types/PlannerTypes'

interface PersonalPlannerCardProps {
  planner: PlannerSummary
  isAuthenticated: boolean
  syncEnabled: boolean | null | undefined
}

export function PersonalPlannerCard({
  planner,
  isAuthenticated,
  syncEnabled,
}: PersonalPlannerCardProps) {
  const { t } = useTranslation(['planner', 'common'])
  const search = useSearch({ strict: false })

  const keywords = planner.selectedKeywords ?? []
  const displayedKeywords = keywords.slice(0, PLANNER_LIST.MAX_KEYWORDS_DISPLAY)
  const hasMoreKeywords = keywords.length > PLANNER_LIST.MAX_KEYWORDS_DISPLAY

  const status = deriveSaveStatus(planner, isAuthenticated, syncEnabled)

  return (
    <Link to="/planner/md/$id" params={{ id: planner.id }} search={search} className="block">
      <div className="bg-card border border-border rounded-lg p-4 h-full hover:border-primary/50 transition-colors cursor-pointer">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-1 flex-wrap min-w-0">
            <span
              className="px-2 py-0.5 text-xs font-medium rounded shrink-0 whitespace-nowrap"
              style={categoryBadgeStyle(planner.category)}
            >
              <MdCategoryLabel category={planner.category} />
            </span>

            {displayedKeywords.map((keyword) => (
              <img
                key={keyword}
                src={getKeywordIconPath(keyword)}
                alt={keyword}
                className="size-5 object-contain"
              />
            ))}
            {hasMoreKeywords && (
              <span className={SECTION_STYLES.TEXT.captionSmall}>
                +{keywords.length - PLANNER_LIST.MAX_KEYWORDS_DISPLAY}
              </span>
            )}
          </div>

          <div className="shrink-0 flex justify-end">
            <PlannerStatusIcon status={status} />
          </div>
        </div>

        <h3 className="line-clamp-2 text-sm font-medium min-h-[2.5rem] mb-2">
          {planner.title || t('untitled')}
        </h3>

        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <Clock className="size-3" />
          {formatCompactDate(planner.lastModifiedAt)}
        </p>
      </div>
    </Link>
  )
}
