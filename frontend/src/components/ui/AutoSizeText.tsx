import { useRef, useLayoutEffect, useState, useCallback } from 'react'

import { AUTOSIZE_MEASURE_RETRY_FRAMES } from '@/lib/constants'

interface AutoSizeTextProps {
  text: string
  /** Requested width in px. The laid-out width of the rendered box wins when they differ. */
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
 * - Re-measures once the web fonts have finished loading
 * - Retries measurement while the box is still zero-sized
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
  const rootRef = useRef<HTMLDivElement>(null)
  const measureRef = useRef<HTMLDivElement>(null)
  const [fontSize, setFontSize] = useState(maxFontSize)
  const [shouldWrap, setShouldWrap] = useState(false)
  const rafRef = useRef<number | undefined>(undefined)

  const lines = text.split('\n')
  const styleKey = JSON.stringify(style)

  const measure = useCallback(() => {
    const rootEl = rootRef.current
    const measureEl = measureRef.current
    if (!rootEl || !measureEl) return false

    // Fractional widths throughout: an integer offsetWidth rounds the ratio down
    // and lets the scaled text render wider than the box that clips it.
    const availableWidth = rootEl.getBoundingClientRect().width
    if (availableWidth === 0) return false

    const lineSpans = measureEl.querySelectorAll<HTMLSpanElement>('[data-measure]')
    if (lineSpans.length === 0) return false

    let maxNaturalWidth = 0
    lineSpans.forEach((span) => {
      maxNaturalWidth = Math.max(maxNaturalWidth, span.getBoundingClientRect().width)
    })

    if (maxNaturalWidth === 0) return false

    let calculatedFontSize: number
    if (maxNaturalWidth <= availableWidth) {
      calculatedFontSize = maxFontSize
    } else {
      calculatedFontSize = (availableWidth / maxNaturalWidth) * maxFontSize
    }

    const needsWrap = calculatedFontSize < minFontSize
    setShouldWrap(needsWrap)

    const clampedFontSize = Math.min(Math.max(calculatedFontSize, minFontSize), maxFontSize)
    setFontSize(clampedFontSize)
    return true
  }, [minFontSize, maxFontSize])

  useLayoutEffect(() => {
    let cancelled = false

    const cancelPending = () => {
      if (rafRef.current !== undefined) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = undefined
      }
    }

    const measureWithRetry = () => {
      cancelPending()
      const attempt = (framesLeft: number) => {
        if (cancelled || measure()) return
        if (framesLeft <= 0) return
        rafRef.current = requestAnimationFrame(() => attempt(framesLeft - 1))
      }
      attempt(AUTOSIZE_MEASURE_RETRY_FRAMES)
    }

    measureWithRetry()

    // The first pass forces layout, so any face this text needs is loading by now.
    const fonts: FontFaceSet | undefined = document.fonts
    if (fonts && fonts.status !== 'loaded') {
      void fonts.ready.then(() => {
        if (!cancelled) measureWithRetry()
      })
    }

    return () => {
      cancelled = true
      cancelPending()
    }
  }, [text, width, minFontSize, maxFontSize, styleKey, measure])

  // Extract font-related styles for measurement (exclude fontSize as we control it)
  const { fontSize: _ignoredFontSize, ...fontStyles } = style || {}

  return (
    <div
      ref={rootRef}
      className={className}
      style={{ width, position: 'relative', overflow: 'hidden', ...fontStyles }}
    >
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
          <span key={index} data-measure style={{ display: 'block', whiteSpace: 'nowrap' }}>
            {line || '\u00A0'}
          </span>
        ))}
      </div>

      {/* Visible text with calculated fontSize */}
      {lines.length === 1 ? (
        <span
          style={{
            ...fontStyles,
            fontSize: `${fontSize}px`,
            lineHeight: lineHeightProp,
            whiteSpace: shouldWrap ? 'normal' : 'nowrap',
            wordBreak: shouldWrap ? 'keep-all' : undefined,
          }}
        >
          {coloredContent ?? text}
        </span>
      ) : (
        <div style={{ ...fontStyles, fontSize: `${fontSize}px`, lineHeight: lineHeightProp }}>
          {coloredContent ??
            lines.map((line, index) => (
              <span
                key={index}
                style={{
                  display: 'block',
                  whiteSpace: shouldWrap ? 'normal' : 'nowrap',
                  wordBreak: shouldWrap ? 'keep-all' : undefined,
                }}
              >
                {line}
              </span>
            ))}
        </div>
      )}
    </div>
  )
}
