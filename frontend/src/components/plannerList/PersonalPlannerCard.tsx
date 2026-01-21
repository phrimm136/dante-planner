import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Clock, CheckCircle } from 'lucide-react'

import { cn } from '@/lib/utils'
import { formatPlannerDate } from '@/lib/formatDate'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { MD_CATEGORY_STYLES, PLANNER_STATUS_BADGE_STYLES, PLANNER_LIST } from '@/lib/constants'

import type { PlannerStatusBadge, MDCategory } from '@/lib/constants'
import type { PlannerSummary } from '@/types/PlannerTypes'

interface PersonalPlannerCardProps {
  planner: PlannerSummary
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Whether sync is enabled (null = not chosen, true = enabled, false = disabled) */
  syncEnabled: boolean | null | undefined
}

/**
 * Card for displaying personal planner summary in My Plans view.
 * Different from PublishedPlannerCard (community) - shows sync status instead of votes.
 *
 * Layout:
 * - Top row: Floor badge + keywords (left), indicator (right)
 * - Title: text-sm, line-clamp-2
 * - Bottom: Date only
 *
 * Indicator states (mutually exclusive, priority order):
 * 1. Published + draft changes → "Unpublished change"
 * 2. Published + saved → "Published"
 * 3. Auth + sync ON + draft → "Unsynced"
 * 4. Auth + sync ON + saved → ✓ Synced icon
 * 5. Guest or sync OFF + draft → "Draft"
 * 6. Guest or sync OFF + saved → (nothing)
 */
export function PersonalPlannerCard({ planner, isAuthenticated, syncEnabled }: PersonalPlannerCardProps) {
  const { t } = useTranslation(['planner', 'common'])

  const categoryClass =
    planner.category in MD_CATEGORY_STYLES
      ? MD_CATEGORY_STYLES[planner.category as MDCategory]
      : 'bg-muted text-muted-foreground'

  // Keywords display (max 3 icons + overflow indicator)
  const keywords = planner.selectedKeywords ?? []
  const displayedKeywords = keywords.slice(0, PLANNER_LIST.MAX_KEYWORDS_DISPLAY)
  const hasMoreKeywords = keywords.length > PLANNER_LIST.MAX_KEYWORDS_DISPLAY

  // Determine indicator state (priority-based cascade)
  let indicatorType: 'badge' | 'icon' | 'none' = 'none'
  let statusBadge: PlannerStatusBadge | null = null
  let showSyncedIcon = false
  let showPublishedBadge = false

  if (planner.published === true) {
    if (planner.status === 'draft') {
      // Published but has unsaved local changes
      indicatorType = 'badge'
      statusBadge = 'UNPUBLISHED'
    } else {
      // Published and saved
      indicatorType = 'badge'
      showPublishedBadge = true
    }
  } else if (isAuthenticated && syncEnabled === true) {
    if (planner.status === 'draft' || planner.savedAt === null) {
      // Authenticated with sync enabled but has unsaved changes
      indicatorType = 'badge'
      statusBadge = 'UNSYNCED'
    } else {
      // Authenticated with sync enabled and saved
      indicatorType = 'icon'
      showSyncedIcon = true
    }
  } else {
    // Guest or sync disabled
    if (planner.status === 'draft' || planner.savedAt === null) {
      indicatorType = 'badge'
      statusBadge = 'DRAFT'
    }
    // If saved with sync off, show nothing
  }

  // Status badge labels
  const statusBadgeLabels: Record<PlannerStatusBadge, string> = {
    DRAFT: t('pages.plannerList.status.draft'),
    UNSYNCED: t('pages.plannerList.status.unsynced'),
    UNPUBLISHED: t('pages.plannerList.status.unpublished'),
  }

  return (
    <Link
      to="/planner/md/$id"
      params={{ id: planner.id }}
      className="block"
    >
      <div className="bg-card border border-border rounded-lg p-4 h-full hover:border-primary/50 transition-colors cursor-pointer">
        {/* Top row: Floor badge + keywords (left), indicator (right) */}
        <div className="flex items-center justify-between gap-2 mb-2">
          {/* Left: Floor badge + keywords inline */}
          <div className="flex items-center gap-1 flex-wrap min-w-0">
            <span
              className={cn(
                'px-2 py-0.5 text-xs font-medium rounded shrink-0 whitespace-nowrap',
                categoryClass
              )}
            >
              {t(`pages.plannerList.mdCategory.${planner.category}`)}
            </span>

            {/* Keywords (icons inline with floor badge) */}
            {displayedKeywords.map((keyword) => (
              <img
                key={keyword}
                src={getKeywordIconPath(keyword)}
                alt={keyword}
                className="size-5 object-contain"
              />
            ))}
            {hasMoreKeywords && (
              <span className="text-xs text-muted-foreground">
                +{keywords.length - PLANNER_LIST.MAX_KEYWORDS_DISPLAY}
              </span>
            )}
          </div>

          {/* Right: Indicator (reserve space for layout stability) */}
          <div className="shrink-0 min-w-[1rem] flex justify-end">
            {indicatorType === 'badge' && statusBadge && (
              <span
                className={cn(
                  'px-1.5 py-0.5 text-[10px] font-medium rounded whitespace-nowrap',
                  PLANNER_STATUS_BADGE_STYLES[statusBadge]
                )}
              >
                {statusBadgeLabels[statusBadge]}
              </span>
            )}
            {indicatorType === 'badge' && showPublishedBadge && (
              <span className="px-1.5 py-0.5 text-[10px] font-medium rounded whitespace-nowrap bg-primary/20 text-primary">
                {t('pages.plannerList.status.published')}
              </span>
            )}
            {indicatorType === 'icon' && showSyncedIcon && (
              <span className="flex items-center gap-1 text-xs text-primary">
                <CheckCircle className="size-3" />
              </span>
            )}
          </div>
        </div>

        {/* Title (text-sm for consistency with PlannerCard) */}
        <h3 className="line-clamp-2 text-sm font-medium min-h-[2.5rem] mb-2">
          {planner.title || t('untitled')}
        </h3>

        {/* Last modified */}
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <Clock className="size-3" />
          {formatPlannerDate(planner.lastModifiedAt)}
        </p>
      </div>
    </Link>
  )
}
