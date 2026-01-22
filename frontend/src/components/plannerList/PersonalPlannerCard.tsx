import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Clock } from 'lucide-react'

import { formatPlannerDate } from '@/lib/formatDate'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { MD_CATEGORY_COLORS, MD_CATEGORY_TEXT_COLORS, PLANNER_LIST } from '@/lib/constants'
import { PlannerStatusIcon } from './PlannerStatusIcon'

import type { MDCategory } from '@/lib/constants'
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
 * 1. Published + draft changes → unpublishedChanges (AlertCircle)
 * 2. Published + saved → published (Globe)
 * 3. Auth + sync ON + draft → unsynced (CloudUpload)
 * 4. Auth + sync ON + saved → synced (CheckCircle)
 * 5. Guest or sync OFF + draft → draft (Circle outline)
 * 6. Guest or sync OFF + saved → saved (Circle filled)
 */
export function PersonalPlannerCard({ planner, isAuthenticated, syncEnabled }: PersonalPlannerCardProps) {
  const { t } = useTranslation(['planner', 'common'])

  const categoryBgColor = planner.category in MD_CATEGORY_COLORS
    ? MD_CATEGORY_COLORS[planner.category as MDCategory]
    : undefined
  const categoryTextColor = planner.category in MD_CATEGORY_TEXT_COLORS
    ? MD_CATEGORY_TEXT_COLORS[planner.category as MDCategory]
    : undefined

  // Keywords display (max 3 icons + overflow indicator)
  const keywords = planner.selectedKeywords ?? []
  const displayedKeywords = keywords.slice(0, PLANNER_LIST.MAX_KEYWORDS_DISPLAY)
  const hasMoreKeywords = keywords.length > PLANNER_LIST.MAX_KEYWORDS_DISPLAY

  // Determine status (priority-based cascade)
  let status: 'draft' | 'saved' | 'unsynced' | 'synced' | 'published' | 'unpublishedChanges' | null = null

  if (planner.published === true) {
    if (planner.status === 'draft') {
      // Published but has unsaved local changes
      status = 'unpublishedChanges'
    } else {
      // Published and saved
      status = 'published'
    }
  } else if (isAuthenticated && syncEnabled === true) {
    if (planner.status === 'draft' || planner.savedAt === null) {
      // Authenticated with sync enabled but has unsaved changes
      status = 'unsynced'
    } else {
      // Authenticated with sync enabled and saved
      status = 'synced'
    }
  } else {
    // Guest or sync disabled
    if (planner.status === 'draft' || planner.savedAt === null) {
      status = 'draft'
    } else {
      // Saved locally with sync off
      status = 'saved'
    }
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
              className="px-2 py-0.5 text-xs font-medium rounded shrink-0 whitespace-nowrap"
              style={{
                backgroundColor: categoryBgColor,
                color: categoryTextColor,
              }}
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

          {/* Right: Status indicator icon */}
          <div className="shrink-0 flex justify-end">
            {status && <PlannerStatusIcon status={status} />}
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
