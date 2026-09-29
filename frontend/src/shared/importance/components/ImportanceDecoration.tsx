import type { CSSProperties } from 'react'

import { cn } from '@/lib/utils'
import { getCautionStripePath, getImportanceGlowPath, getImportanceIconPath } from '@/shared/assets'
import type { ImportancePalette } from '@/shared/gameData'
import {
  importanceCssVariables,
  importanceGeometry,
  importanceStyle,
  stripeTileWidth,
  type ImportanceVariant,
} from '../lib/importanceTable'

interface ImportanceDecorationProps {
  level: number
  palette: ImportancePalette
  variant: ImportanceVariant
  isLocked?: boolean
}

export function ImportanceDecoration({
  level,
  palette,
  variant,
  isLocked = false,
}: ImportanceDecorationProps) {
  const style = importanceStyle(level, palette)
  if (style === undefined) return null

  const stripeStyle = {
    maskImage: `url(${getCautionStripePath()})`,
    '--band-h': '100cqh',
    '--tile-w': stripeTileWidth(),
  } as CSSProperties

  return (
    <div
      aria-hidden="true"
      data-slot="importance-decoration"
      data-importance-variant={variant}
      className="pointer-events-none absolute inset-0 -z-10"
      style={importanceCssVariables(style, importanceGeometry(variant, isLocked))}
    >
      <span
        data-slot="importance-icon"
        className={cn(
          'importance-icon',
          style.flickerTarget === 'icon' && 'importance-flicker-icon',
        )}
      >
        <img
          data-slot="importance-icon-image"
          src={getImportanceIconPath(palette, style.tier)}
          alt=""
          className="size-full object-contain"
        />
        {style.hasGlow && (
          <img
            data-slot="importance-glow"
            src={getImportanceGlowPath(palette, style.tier)}
            alt=""
            className="importance-glow importance-flicker"
          />
        )}
      </span>
      <div data-slot="importance-band-top" className="importance-band importance-band-top">
        <div className="importance-stripe importance-stripe-top" style={stripeStyle} />
      </div>
      <div data-slot="importance-band-bottom" className="importance-band importance-band-bottom">
        <div className="importance-stripe" style={stripeStyle} />
      </div>
    </div>
  )
}
