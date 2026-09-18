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
  title?: string
  titleAlign?: TitleAlign
  children: ReactNode
  className?: string
}

export function LabeledPanel({
  title,
  titleAlign = 'center',
  children,
  className,
}: LabeledPanelProps) {
  return (
    <div className={cn('border rounded p-3 space-y-2', className)}>
      {title !== undefined && (
        <div className={cn('font-semibold text-sm', TITLE_ALIGN[titleAlign])}>{title}</div>
      )}
      {children}
    </div>
  )
}
