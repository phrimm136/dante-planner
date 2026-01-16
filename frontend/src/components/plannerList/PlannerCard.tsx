import { useTranslation } from 'react-i18next'
import { ThumbsUp, Eye, Bookmark } from 'lucide-react'

import { cn } from '@/lib/utils'
import { formatPlannerDate } from '@/lib/formatDate'
import { formatUsername } from '@/lib/formatUsername'
import { PLANNER_LIST, MD_CATEGORY_STYLES, PLANNER_STATUS_BADGE_STYLES } from '@/lib/constants'

import type { PublicPlanner } from '@/types/PlannerListTypes'
import type { PlannerStatusBadge } from '@/lib/constants'

interface PlannerCardProps {
  /** Planner data to display */
  planner: PublicPlanner
  /** Whether to show bookmark indicator (only in community view for logged-in users) */
  showBookmark?: boolean
  /** Status indicator badge type (Draft, Unsynced, Unpublished) */
  statusBadge?: PlannerStatusBadge | null
  /** Optional context menu handler */
  onContextMenu?: (e: React.MouseEvent) => void
  /** Additional className */
  className?: string
}

/**
 * Pure view-only card component for displaying planner summary.
 * Does NOT include interactive logic (Link, onClick, etc.)
 * Parent component is responsible for wrapping with Link or handling clicks.
 *
 * Status badge logic (determined by parent):
 * - null/undefined = Normal/synced (no badge)
 * - DRAFT = planner.metadata.status === 'draft' or never manually saved
 * - UNSYNCED = authenticated + syncEnabled + local changes not pushed
 * - UNPUBLISHED = published + local differs from server version
 *
 * @example
 * // In PlannerList
 * <Link to="/planner/md/$id" params={{ id: planner.id }}>
 *   <PlannerCard planner={planner} showBookmark={isAuthenticated} />
 * </Link>
 *
 * // With context menu
 * <PlannerCardContextMenu planner={planner}>
 *   <PlannerCard planner={planner} />
 * </PlannerCardContextMenu>
 */
export function PlannerCard({
  planner,
  showBookmark = false,
  statusBadge,
  onContextMenu,
  className,
}: PlannerCardProps) {
  const { t } = useTranslation(['planner', 'common'])
  const {
    title,
    category,
    selectedKeywords,
    upvotes,
    // downvotes,  // TODO: Add when backend supports it
    viewCount,
    authorUsernameKeyword,
    authorUsernameSuffix,
    lastModifiedAt,
    isBookmarked,
  } = planner

  // Status badge labels
  const statusBadgeLabels: Record<PlannerStatusBadge, string> = {
    DRAFT: t('pages.plannerList.status.draft', 'Draft'),
    UNSYNCED: t('pages.plannerList.status.unsynced', 'Unsynced'),
    UNPUBLISHED: t('pages.plannerList.status.unpublished', 'Unpublished changes'),
  }

  // Limit displayed keywords (handle nullable)
  const keywords = selectedKeywords ?? []
  const displayedKeywords = keywords.slice(0, PLANNER_LIST.MAX_KEYWORDS_DISPLAY)
  const hasMoreKeywords = keywords.length > PLANNER_LIST.MAX_KEYWORDS_DISPLAY

  return (
    <div
      className={cn(
        'selectable group relative bg-card border border-border rounded-lg p-4 cursor-pointer',
        className
      )}
      onContextMenu={onContextMenu}
    >
      {/* Category Badge + Status Badge */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              'px-2 py-0.5 text-xs font-medium rounded',
              // MD_CATEGORY_STYLES only has MD categories, fallback for RR
              category in MD_CATEGORY_STYLES
                ? MD_CATEGORY_STYLES[category as keyof typeof MD_CATEGORY_STYLES]
                : 'bg-muted text-muted-foreground'
            )}
          >
            {category}
          </span>

          {/* Status indicator badge (subtle, shows sync state) */}
          {statusBadge && (
            <span
              className={cn(
                'px-1.5 py-0.5 text-[10px] font-medium rounded',
                PLANNER_STATUS_BADGE_STYLES[statusBadge]
              )}
            >
              {statusBadgeLabels[statusBadge]}
            </span>
          )}
        </div>

        {/* Bookmark indicator (visible in community view for authenticated users) */}
        {showBookmark && isBookmarked && (
          <Bookmark className="size-4 fill-primary text-primary" />
        )}
      </div>

      {/* Title (max 2 lines) */}
      <h3 className="text-sm font-medium line-clamp-2 min-h-[2.5rem] mb-2">
        {title}
      </h3>

      {/* Keywords (max 3 chips) */}
      {displayedKeywords.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-3">
          {displayedKeywords.map((keyword) => (
            <span
              key={keyword}
              className="px-1.5 py-0.5 text-xs bg-muted text-muted-foreground rounded"
            >
              {keyword}
            </span>
          ))}
          {hasMoreKeywords && (
            <span className="px-1.5 py-0.5 text-xs text-muted-foreground">
              +{keywords.length - PLANNER_LIST.MAX_KEYWORDS_DISPLAY}
            </span>
          )}
        </div>
      )}

      {/* Stats row */}
      <div className="flex items-center gap-3 text-xs text-muted-foreground mb-2">
        {/* Upvotes */}
        <span className="flex items-center gap-1">
          <ThumbsUp className="size-3" />
          {upvotes}
        </span>

        {/* Downvotes - TODO: Add when backend supports it */}
        {/* <span className="flex items-center gap-1">
          <ThumbsDown className="size-3" />
          {downvotes}
        </span> */}

        {/* Views */}
        <span className="flex items-center gap-1">
          <Eye className="size-3" />
          {viewCount}
        </span>
      </div>

      {/* Date & Author */}
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        {/* formatPlannerDate: <24h shows HH:mm, >=24h shows MM/DD */}
        <span>{lastModifiedAt ? formatPlannerDate(lastModifiedAt) : '-'}</span>
        <span className="truncate max-w-[60%]">
          {formatUsername(authorUsernameKeyword, authorUsernameSuffix)}
        </span>
      </div>
    </div>
  )
}
