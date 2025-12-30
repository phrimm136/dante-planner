import { useIsBreakpoint } from '@/hooks/use-is-breakpoint'
import { DETAIL_PAGE } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface DetailPageLayoutProps {
  /** Left column content (Info section) */
  leftColumn: React.ReactNode
  /** Right column content (desktop) or null if using mobile tabs */
  rightColumn: React.ReactNode
  /** Mobile tabs content (shown below left column on mobile) */
  mobileTabsContent?: React.ReactNode
}

/**
 * DetailPageLayout - Reusable two-column detail page layout
 *
 * Desktop (>= 1024px): 4:6 ratio two-column grid
 * Mobile (< 1024px): Single column with Info at top + tabs below
 *
 * Pattern: Uses DETAIL_PAGE constants for column ratios
 */
export function DetailPageLayout({
  leftColumn,
  rightColumn,
  mobileTabsContent,
}: DetailPageLayoutProps) {
  const isMobile = useIsBreakpoint('max', DETAIL_PAGE.BREAKPOINT_LG)

  if (isMobile) {
    // Mobile: Single column with Info at top, then tabs
    return (
      <div className="container mx-auto p-4 sm:p-6">
        <div className="space-y-6">
          {/* Info section always at top */}
          <div className="space-y-6">{leftColumn}</div>
          {/* Tabs below info */}
          {mobileTabsContent}
        </div>
      </div>
    )
  }

  // Desktop: 4:6 ratio two-column grid (using 10-column grid)
  return (
    <div className="container mx-auto p-8">
      <div className="grid grid-cols-10 gap-6">
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_LEFT)}>{leftColumn}</div>
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_RIGHT)}>{rightColumn}</div>
      </div>
    </div>
  )
}
