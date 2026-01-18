import { useRef, useLayoutEffect, useState } from 'react'

interface AutoSizeTextProps {
  text: string
  width: number
  className?: string
  /** Font styles (fontFamily, letterSpacing, etc.) - applied to both measurement and display */
  style?: React.CSSProperties
  minFontSize?: number
  maxFontSize?: number
  /** Line height multiplier */
  lineHeight?: number
  /** Optional colored content to display instead of text (text is still used for measurement) */
  coloredContent?: React.ReactNode
}

/**
 * Text component that adjusts font size to fit within container width
 *
 * Features:
 * - Calculates actual fontSize (not transform) to prevent overflow
 * - Supports multi-line text with \n separators
 * - All lines use the same font size (determined by the longest line)
 * - Font size is clamped between minFontSize and maxFontSize
 * - If text overflows at minFontSize, wraps to next line (word-break: keep-all)
 *
 * IMPORTANT: Pass fontFamily via style prop for accurate measurement
 */
export function AutoSizeText({
  text,
  width,
  className = '',
  style,
  minFontSize = 8,
  maxFontSize = 30,
  lineHeight: lineHeightProp,
  coloredContent,
}: AutoSizeTextProps) {
  const measureRef = useRef<HTMLDivElement>(null)
  const [fontSize, setFontSize] = useState(maxFontSize)
  const [shouldWrap, setShouldWrap] = useState(false)

  const lines = text.split('\n')

  useLayoutEffect(() => {
    const measureEl = measureRef.current
    if (!measureEl) return

    // Measure each line's natural width at maxFontSize
    const lineSpans = measureEl.querySelectorAll<HTMLSpanElement>('[data-measure]')
    if (lineSpans.length === 0) return

    let maxNaturalWidth = 0
    lineSpans.forEach(span => {
      maxNaturalWidth = Math.max(maxNaturalWidth, span.offsetWidth)
    })

    if (maxNaturalWidth === 0) return

    // Calculate font size needed to fit the widest line
    // If text fits at maxFontSize, use maxFontSize
    // If text is too wide, shrink proportionally
    let calculatedFontSize: number
    if (maxNaturalWidth <= width) {
      // Text fits - use maxFontSize
      calculatedFontSize = maxFontSize
    } else {
      // Text too wide - shrink to fit
      // Formula: newFontSize / maxFontSize = width / naturalWidth
      calculatedFontSize = (width / maxNaturalWidth) * maxFontSize
    }

    // Check if text still doesn't fit at minFontSize
    const needsWrap = calculatedFontSize < minFontSize
    setShouldWrap(needsWrap)

    // Clamp to min/max bounds
    const clampedFontSize = Math.min(Math.max(calculatedFontSize, minFontSize), maxFontSize)
    setFontSize(clampedFontSize)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, width, minFontSize, maxFontSize, JSON.stringify(style)])

  // Extract font-related styles for measurement (exclude fontSize as we control it)
  const { fontSize: _ignoredFontSize, ...fontStyles } = style || {}

  return (
    <div className={className} style={{ width, position: 'relative', ...fontStyles }}>
      {/* Hidden measurement container - uses same font styles at maxFontSize */}
      <div
        ref={measureRef}
        aria-hidden="true"
        style={{
          position: 'absolute',
          visibility: 'hidden',
          pointerEvents: 'none',
          fontSize: `${maxFontSize}px`,
          ...fontStyles,
        }}
      >
        {lines.map((line, index) => (
          <span
            key={index}
            data-measure
            style={{ display: 'block', whiteSpace: 'nowrap' }}
          >
            {line || '\u00A0'}
          </span>
        ))}
      </div>

      {/* Visible text with calculated fontSize */}
      {lines.length === 1 ? (
        <span style={{
          ...fontStyles,
          fontSize: `${fontSize}px`,
          lineHeight: lineHeightProp,
          whiteSpace: shouldWrap ? 'normal' : 'nowrap',
          wordBreak: shouldWrap ? 'keep-all' : undefined,
        }}>
          {coloredContent ?? text}
        </span>
      ) : (
        <div style={{ ...fontStyles, fontSize: `${fontSize}px`, lineHeight: lineHeightProp }}>
          {coloredContent ?? lines.map((line, index) => (
            <span key={index} style={{
              display: 'block',
              whiteSpace: shouldWrap ? 'normal' : 'nowrap',
              wordBreak: shouldWrap ? 'keep-all' : undefined,
            }}>
              {line}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
