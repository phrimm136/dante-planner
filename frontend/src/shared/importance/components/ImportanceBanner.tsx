import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'
import type { ImportancePalette } from '@/shared/gameData'
import { importanceBannerStyle } from '../lib/importanceTable'

interface ImportanceBannerProps {
  level: number
  palette: ImportancePalette
  className: string
  children: ReactNode
}

export function ImportanceBanner({ level, palette, className, children }: ImportanceBannerProps) {
  const style = importanceBannerStyle(level, palette)
  if (style === undefined) return <div className={className}>{children}</div>

  return (
    <div data-slot="importance-banner" className={cn(className, 'importance-banner')} style={style}>
      {children}
    </div>
  )
}
