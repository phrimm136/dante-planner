import { useRef, useLayoutEffect, useState } from 'react'

interface AutoSizeWrappedTextProps {
  text: string
  width: number
  maxLines: number
  className?: string
  /** Font styles (fontFamily, letterSpacing, etc.) - applied to both measurement and display */
  style?: React.CSSProperties
  minFontSize?: number
  maxFontSize?: number
  /** Line height ratio (e.g., 1.2). Use getLineHeightForLanguage() for language-specific values. Default: 1.2 */
  lineHeight?: number
}

/**
 * Text component that word-wraps and adjusts font size to fit within maxLines.
 *
 * Behavior:
 * 1. Enables word wrapping (breaks at word boundaries)
 * 2. Shrinks font size to fit content within maxLines
 * 3. If content exceeds maxLines at minFontSize → truncates with line-clamp
 *
 * Use cases: Identity names, EGO names (variable length, need wrapping)
 *
 * For single-line shrinking without wrap, use AutoSizeText instead.
 */
export function AutoSizeWrappedText({
  text,
  width,
  maxLines,
  className = '',
  style,
  minFontSize = 8,
  maxFontSize = 16,
  lineHeight: lineHeightProp = 1.2,
}: AutoSizeWrappedTextProps) {
  const measureRef = useRef<HTMLDivElement>(null)
  const [fontSize, setFontSize] = useState(maxFontSize)

  const { fontSize: _ignoredFontSize, ...fontStyles } = style || {}

  useLayoutEffect(() => {
    const measureEl = measureRef.current
    if (!measureEl) return

    // Line height multiplier (matches display)
    const lineHeightRatio = lineHeightProp

    // Try font sizes from max to min in 0.5px steps
    for (let size = maxFontSize; size >= minFontSize; size -= 0.5) {
      measureEl.style.fontSize = `${size}px`

      const lineHeight = size * lineHeightRatio
      const maxHeight = lineHeight * maxLines

      if (measureEl.scrollHeight <= maxHeight + 1) {
        setFontSize(size)
        return
      }
    }

    // Couldn't fit even at minFontSize - use minFontSize (line-clamp will truncate)
    setFontSize(minFontSize)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, width, maxLines, minFontSize, maxFontSize, lineHeightProp, JSON.stringify(style)])

  return (
    <div
      className={className}
      style={{
        width,
        position: 'relative',
        ...fontStyles,
      }}
    >
      {/* Hidden measurement container */}
      <div
        ref={measureRef}
        aria-hidden="true"
        style={{
          position: 'absolute',
          visibility: 'hidden',
          pointerEvents: 'none',
          width,
          fontSize: `${maxFontSize}px`,
          lineHeight: lineHeightProp,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          ...fontStyles,
        }}
      >
        {text}
      </div>

      {/* Visible text with line-clamp for overflow protection */}
      <div
        style={{
          fontSize: `${fontSize}px`,
          lineHeight: lineHeightProp,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          display: '-webkit-box',
          WebkitLineClamp: maxLines,
          WebkitBoxOrient: 'vertical' as const,
          overflow: 'hidden',
          ...fontStyles,
        }}
      >
        {text}
      </div>
    </div>
  )
}
