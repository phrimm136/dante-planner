import { useTranslation } from 'react-i18next'
import { ThumbsUp, Eye, Bookmark, Star, Clock, MessageSquare } from 'lucide-react'

import { cn } from '@/lib/utils'
import { formatCompactDate } from '@/lib/formatDate'
import { formatUsername } from '@/lib/formatUsername'
import { getKeywordIconPath } from '@/shared/assets'
import {
  PLANNER_LIST,
  RECOMMENDED_THRESHOLD,
  SECTION_STYLES,
  STAR_ICON_CLASS,
} from '@/lib/constants'
import { MdCategoryLabel } from '../MdCategoryLabel'
import { categoryBadgeStyle } from '../../lib/plannerBadges'

import type { PublicPlanner } from '../../types/PlannerListTypes'

interface PublishedPlannerCardProps {
  planner: PublicPlanner
  showBookmark?: boolean
  onContextMenu?: (e: React.MouseEvent) => void
  className?: string
}

export function PublishedPlannerCard({
  planner,
  showBookmark = false,
  onContextMenu,
  className,
}: PublishedPlannerCardProps) {
  const { i18n } = useTranslation(['planner', 'common'])
  const {
    title,
    category,
    selectedKeywords,
    upvotes,
    // downvotes,  // TODO: Add when backend supports it
    viewCount,
    commentCount,
    authorUsernameEpithet,
    authorUsernameSuffix,
    createdAt,
    isBookmarked,
  } = planner

  const keywords = selectedKeywords ?? []
  const displayedKeywords = keywords.slice(0, PLANNER_LIST.MAX_KEYWORDS_DISPLAY)
  const hasMoreKeywords = keywords.length > PLANNER_LIST.MAX_KEYWORDS_DISPLAY

  return (
    <div
      className={cn(
        'selectable group relative bg-card border border-border rounded-lg p-4 cursor-pointer',
        className,
      )}
      onContextMenu={onContextMenu}
    >
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 flex-wrap min-w-0">
          <span
            className="px-2 py-0.5 text-xs font-medium rounded shrink-0 whitespace-nowrap"
            style={categoryBadgeStyle(category)}
          >
            <MdCategoryLabel category={category} />
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

        <div className="shrink-0 min-w-[1rem] flex justify-end">
          {upvotes >= RECOMMENDED_THRESHOLD && <Star className={cn('size-4', STAR_ICON_CLASS)} />}
          {showBookmark && isBookmarked && (
            <Bookmark className="size-4 fill-primary text-primary" />
          )}
        </div>
      </div>

      <h3 className="line-clamp-2 text-sm font-medium min-h-[2.5rem] mb-2">{title}</h3>

      <div className="flex items-center gap-3 text-xs text-muted-foreground mb-2">
        <span className={SECTION_STYLES.LAYOUT.rowTight}>
          <ThumbsUp className="size-3" />
          {upvotes}
        </span>

        {/* Downvotes - TODO: Add when backend supports it */}

        <span className={SECTION_STYLES.LAYOUT.rowTight}>
          <Eye className="size-3" />
          {viewCount}
        </span>

        <span className={SECTION_STYLES.LAYOUT.rowTight}>
          <MessageSquare className="size-3" />
          {commentCount}
        </span>
      </div>

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span className={SECTION_STYLES.LAYOUT.rowTight}>
          <Clock className="size-3" />
          {createdAt ? formatCompactDate(createdAt) : '-'}
        </span>
        <span className="truncate max-w-[60%]">
          {formatUsername(authorUsernameEpithet, authorUsernameSuffix, i18n.language)}
        </span>
      </div>
    </div>
  )
}
