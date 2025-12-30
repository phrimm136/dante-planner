import { ScrollArea } from '@/components/ui/scroll-area'
import { DETAIL_PAGE } from '@/lib/constants'

interface DetailRightPanelProps {
  /** Sticky selector component at top */
  selector: React.ReactNode
  /** Scrollable content below selector */
  children: React.ReactNode
}

/**
 * DetailRightPanel - Scrollable right panel for detail pages
 *
 * Features:
 * - Sticky selector at top that stays visible while scrolling
 * - Scrollable content area with max height constraint
 * - Uses ScrollArea for custom scrollbar styling
 *
 * Pattern: Follows DetailPageLayout.tsx container structure
 */
export function DetailRightPanel({ selector, children }: DetailRightPanelProps) {
  return (
    <div className="flex flex-col h-full">
      {/* Sticky selector */}
      <div className="sticky top-0 z-10 bg-background pb-4">
        {selector}
      </div>

      {/* Scrollable content */}
      <ScrollArea
        className="flex-1"
        style={{ maxHeight: DETAIL_PAGE.RIGHT_PANEL_MAX_HEIGHT }}
      >
        <div className="space-y-6 pr-4">
          {children}
        </div>
      </ScrollArea>
    </div>
  )
}

export default DetailRightPanel
