import { cn } from '@/lib/utils'
import { CARD_GAP_PX } from '@/lib/constants'
import { useSlotSizePx, type GridRowHeight, type CardSizePx } from '@/shared/cardLayout'

interface ResponsiveCardGridProps {
  size: CardSizePx
  rows?: GridRowHeight
  gap?: number
  children: React.ReactNode
  className?: string
  mobileScale?: number
  ref?: React.Ref<HTMLDivElement>
}

export function ResponsiveCardGrid({
  size,
  rows = 'slot',
  gap = CARD_GAP_PX,
  children,
  className,
  mobileScale = 1,
  ref,
}: ResponsiveCardGridProps) {
  const { widthPx: columnWidthPx, heightPx: rowHeightPx } = useSlotSizePx(size, mobileScale)

  const gridStyle: React.CSSProperties = {
    gridTemplateColumns: `repeat(auto-fill, ${String(columnWidthPx)}px)`,
    gap: `${String(gap)}px`,
    justifyContent: 'center',
    ...(rows === 'slot' && {
      gridAutoRows: `${String(rowHeightPx)}px`,
    }),
  }

  return (
    <div ref={ref} className={cn('grid', className)} style={gridStyle}>
      {children}
    </div>
  )
}
