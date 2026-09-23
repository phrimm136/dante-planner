import type { CSSProperties } from 'react'

import { getCautionStripePath, getImportanceGlowPath, getImportanceIconPath } from '@/shared/assets'
import type { ImportancePalette } from '@/shared/gameData'
import { importanceCssVariables, importanceStyle, stripeTileWidth } from '../lib/importanceTable'

interface ImportanceDecorationProps {
  level: number
  palette: ImportancePalette
  variant: 'skill' | 'passive'
}

export function ImportanceDecoration({ level, palette, variant }: ImportanceDecorationProps) {
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
      style={importanceCssVariables(style)}
    >
      {variant === 'passive' && (
        <div data-slot="importance-panel" className="importance-panel absolute inset-0" />
      )}
      <img
        data-slot="importance-icon"
        src={getImportanceIconPath(palette, style.tier)}
        alt=""
        className="importance-icon absolute top-0 right-0 h-full"
      />
      {style.hasGlow && (
        <span
          data-slot="importance-glow"
          className="importance-flicker absolute top-0 right-0 h-full"
        >
          <img
            src={getImportanceGlowPath(palette, style.tier)}
            alt=""
            className="importance-glow-fade h-full"
          />
        </span>
      )}
      <div data-slot="importance-band-top" className="importance-band top-0">
        <div className="importance-stripe importance-stripe-top" style={stripeStyle} />
      </div>
      <div data-slot="importance-band-bottom" className="importance-band bottom-0">
        <div className="importance-stripe" style={stripeStyle} />
      </div>
    </div>
  )
}
