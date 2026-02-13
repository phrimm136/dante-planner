import { useRef, useLayoutEffect, useState, useCallback } from 'react'

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
 * - Retries measurement if element is not yet laid out (offsetWidth = 0)
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
  const rafRef = useRef<number | undefined>(undefined)

  const lines = text.split('\n')

  const measure = useCallback(() => {
    const measureEl = measureRef.current
    if (!measureEl) return false

    const lineSpans = measureEl.querySelectorAll<HTMLSpanElement>('[data-measure]')
    if (lineSpans.length === 0) return false

    let maxNaturalWidth = 0
    lineSpans.forEach(span => {
      maxNaturalWidth = Math.max(maxNaturalWidth, span.offsetWidth)
    })

    if (maxNaturalWidth === 0) return false

    let calculatedFontSize: number
    if (maxNaturalWidth <= width) {
      calculatedFontSize = maxFontSize
    } else {
      calculatedFontSize = (width / maxNaturalWidth) * maxFontSize
    }

    const needsWrap = calculatedFontSize < minFontSize
    setShouldWrap(needsWrap)

    const clampedFontSize = Math.min(Math.max(calculatedFontSize, minFontSize), maxFontSize)
    setFontSize(clampedFontSize)
    return true
  }, [width, minFontSize, maxFontSize])

  useLayoutEffect(() => {
    if (rafRef.current) {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = undefined
    }

    if (measure()) return

    // Element not laid out yet — retry after each paint until measurement succeeds
    const retry = () => {
      if (measure()) return
      rafRef.current = requestAnimationFrame(retry)
    }
    rafRef.current = requestAnimationFrame(retry)

    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = undefined
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, width, minFontSize, maxFontSize, JSON.stringify(style), measure])

  // Extract font-related styles for measurement (exclude fontSize as we control it)
  const { fontSize: _ignoredFontSize, ...fontStyles } = style || {}

  return (
    <div className={className} style={{ width, position: 'relative', overflow: 'hidden', ...fontStyles }}>
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
