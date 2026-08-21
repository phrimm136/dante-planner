import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

type TitleAlign = 'left' | 'center' | 'right'

/** Full literals so Tailwind's scanner sees each class */
const TITLE_ALIGN: Record<TitleAlign, string> = {
  left: 'text-left',
  center: 'text-center',
  right: 'text-right',
}

interface LabeledPanelProps {
  /** Already-translated heading; omitted → no title row */
  title?: string
  titleAlign?: TitleAlign
  children: ReactNode
  /** Parent-context layout, e.g. "h-full", "flex-1" */
  className?: string
}

/**
 * Bordered detail-page panel with an optional aligned title row.
 * The shared shell behind stat panels, metadata boxes, and keyword rows.
 */
export function LabeledPanel({
  title,
  titleAlign = 'center',
  children,
  className,
}: LabeledPanelProps) {
  return (
    <div className={cn('border rounded p-4 space-y-4', className)}>
      {title !== undefined && (
        <div className={cn('font-semibold text-sm', TITLE_ALIGN[titleAlign])}>{title}</div>
      )}
      {children}
    </div>
  )
}
