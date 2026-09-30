import type { CSSProperties, ReactNode } from 'react'

import { cn } from '@/lib/utils'
import type { ImportancePalette } from '@/shared/gameData'
import { importanceBannerStyle } from '../lib/importanceTable'

interface ImportanceBannerProps {
  level: number
  palette: ImportancePalette
  className: string
  style?: CSSProperties
  children: ReactNode
}

export function ImportanceBanner({
  level,
  palette,
  className,
  style,
  children,
}: ImportanceBannerProps) {
  const bannerStyle = importanceBannerStyle(level, palette)
  if (bannerStyle === undefined)
    return (
      <div className={className} style={style}>
        {children}
      </div>
    )

  return (
    <div
      data-slot="importance-banner"
      className={cn(className, 'importance-banner')}
      style={{ ...bannerStyle, ...style }}
    >
      {children}
    </div>
  )
}
