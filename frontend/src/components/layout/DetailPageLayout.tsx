import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { LG_BREAKPOINT_PX, DETAIL_PAGE, SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface DetailPageLayoutProps {
  leftColumn: React.ReactNode
  rightColumn: React.ReactNode
  mobileTabsContent?: React.ReactNode
}

export function DetailPageLayout({
  leftColumn,
  rightColumn,
  mobileTabsContent,
}: DetailPageLayoutProps) {
  const isMobile = useIsBreakpoint('max', LG_BREAKPOINT_PX)

  if (isMobile) {
    return (
      <div className="container mx-auto p-4 sm:p-6">
        <div className="space-y-6">
          <div className="space-y-6">{leftColumn}</div>
          {mobileTabsContent}
        </div>
      </div>
    )
  }

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="grid grid-cols-10 gap-6">
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_LEFT)}>{leftColumn}</div>
        <div className={cn('col-span-10 space-y-6', DETAIL_PAGE.COLUMN_RIGHT)}>{rightColumn}</div>
      </div>
    </div>
  )
}
