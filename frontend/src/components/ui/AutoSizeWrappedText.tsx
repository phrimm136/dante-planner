import { useRef, useLayoutEffect, useState } from 'react'

import { AUTOSIZE_MEASURE_RETRY_FRAMES } from '@/lib/constants'

interface AutoSizeWrappedTextProps {
  text: string
  /** Requested width in px. The laid-out width of the rendered box wins when they differ. */
  width: number
  maxLines: number
  className?: string
  /** Font styles (fontFamily, letterSpacing, etc.) - applied to both measurement and display */
  style?: React.CSSProperties
  minFontSize?: number
  maxFontSize?: number
  /** Line height ratio (e.g., 1.2). Use getLineHeightForLanguage() for language-specific values. Default: 1.2 */
  lineHeight?: number
  /** Word break behavior:
   * - 'keep-all': Preserves all CJK words (Korean, Japanese, Chinese stay together)
   * - 'break-all': Breaks at any character
   * - 'normal': Korean stays together, kanji/hanzi can break (mixed script handling)
   * Default: 'keep-all'
   */
  wordBreak?: 'keep-all' | 'break-all' | 'normal'
}

/**
 * Text component that word-wraps and adjusts font size to fit within maxLines.
 *
 * Behavior:
 * 1. Enables word wrapping (breaks at word boundaries)
 * 2. Shrinks font size to fit content within maxLines
 * 3. If content exceeds maxLines at minFontSize → truncates with line-clamp
 * 4. Re-measures once the web fonts have finished loading
 * 5. Retries measurement while the box is still zero-sized
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
  wordBreak: wordBreakProp = 'keep-all',
}: AutoSizeWrappedTextProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const measureRef = useRef<HTMLDivElement>(null)
  const [fontSize, setFontSize] = useState(maxFontSize)
  const rafRef = useRef<number | undefined>(undefined)

  const { fontSize: _ignoredFontSize, ...fontStyles } = style || {}
  const styleKey = JSON.stringify(style)

  useLayoutEffect(() => {
    let cancelled = false

    const measure = () => {
      const rootEl = rootRef.current
      const measureEl = measureRef.current
      if (!rootEl || !measureEl) return false

      const availableWidth = rootEl.offsetWidth
      if (availableWidth === 0) return false
      measureEl.style.width = `${availableWidth}px`

      // Each probe writes fontSize then reads scrollHeight, forcing a synchronous
      // layout of the whole document. Grid indices run 0 (minFontSize) upward in
      // 0.5px steps; a binary search costs ~log2(n) probes instead of n.
      const steps = Math.max(0, Math.round((maxFontSize - minFontSize) / 0.5))
      const sizeAt = (index: number) => minFontSize + index * 0.5

      const fits = (size: number) => {
        measureEl.style.fontSize = `${size}px`
        return measureEl.scrollHeight <= size * lineHeightProp * maxLines + 1
      }

      // Text that already fits at the largest size is the common case: one probe.
      if (fits(maxFontSize)) {
        setFontSize(maxFontSize)
        return true
      }

      let low = 0
      let high = steps - 1
      let best = -1
      while (low <= high) {
        const mid = (low + high) >> 1
        if (fits(sizeAt(mid))) {
          best = mid
          low = mid + 1
        } else {
          high = mid - 1
        }
      }

      // Nothing fits, even at minFontSize - line-clamp truncates instead
      setFontSize(best === -1 ? minFontSize : sizeAt(best))
      return true
    }

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
  }, [text, width, maxLines, minFontSize, maxFontSize, lineHeightProp, styleKey])

  return (
    <div
      ref={rootRef}
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
          wordBreak: wordBreakProp,
          overflowWrap: 'break-word',
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
          wordBreak: wordBreakProp,
          overflowWrap: 'break-word',
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
