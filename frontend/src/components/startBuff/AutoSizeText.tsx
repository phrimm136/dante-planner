import { useRef, useLayoutEffect, useState } from 'react'

interface AutoSizeTextProps {
  text: string
  width: number
  className?: string
  minFontSize?: number
  maxFontSize?: number
}

/**
 * Text component that scales to fill the container width
 * - Short text: font scales up to fill width (up to maxFontSize)
 * - Long text: font scales down to fit (down to minFontSize)
 */
export function AutoSizeText({
  text,
  width,
  className = '',
  minFontSize = 8,
  maxFontSize = 14,
}: AutoSizeTextProps) {
  const measureRef = useRef<HTMLSpanElement>(null)
  const [scale, setScale] = useState(1)

  // useLayoutEffect prevents flicker by running before paint
  useLayoutEffect(() => {
    const measureEl = measureRef.current
    if (!measureEl) return

    // Measure natural width from hidden element
    const naturalWidth = measureEl.offsetWidth

    if (naturalWidth === 0) return

    // Calculate scale to fill width
    const newScale = width / naturalWidth
    setScale(newScale)
  }, [text, width])

  // Clamp scale based on font size limits
  const clampedScale = Math.min(Math.max(scale, minFontSize / maxFontSize), 1.5)

  return (
    <div
      className={className}
      style={{ width, overflow: 'hidden', position: 'relative' }}
    >
      {/* Hidden element for measuring natural width */}
      <span
        ref={measureRef}
        style={{
          fontSize: `${maxFontSize}px`,
          whiteSpace: 'nowrap',
          position: 'absolute',
          visibility: 'hidden',
          pointerEvents: 'none',
        }}
      >
        {text}
      </span>
      {/* Visible scaled text */}
      <span
        style={{
          fontSize: `${maxFontSize}px`,
          whiteSpace: 'nowrap',
          display: 'inline-block',
          transform: `scale(${clampedScale})`,
          transformOrigin: 'left center',
        }}
      >
        {text}
      </span>
    </div>
  )
}
