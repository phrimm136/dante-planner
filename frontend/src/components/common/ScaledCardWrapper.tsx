import { useState, useEffect } from 'react'
import { CARD_GRID } from '@/lib/constants'

interface ScaledCardWrapperProps {
  /** Card width in pixels (unscaled) */
  cardWidth: number
  /** Card height in pixels (unscaled) */
  cardHeight: number
  /** Mobile scale factor (0-1, e.g., 0.6 or 0.8) */
  mobileScale: number
  /** Additional CSS classes for the wrapper */
  className?: string
  /** Card content */
  children: React.ReactNode
}

/**
 * Wrapper for consistent mobile card scaling.
 * Uses window width detection to apply transform only on mobile.
 * Single element with dynamic styling - no duplicate rendering.
 *
 * @example
 * <ScaledCardWrapper mobileScale={0.8} cardWidth={96} cardHeight={96}>
 *   <EGOGiftCard gift={gift} />
 * </ScaledCardWrapper>
 */
export function ScaledCardWrapper({
  cardWidth,
  cardHeight,
  mobileScale,
  className,
  children,
}: ScaledCardWrapperProps) {
  const [isDesktop, setIsDesktop] = useState(typeof window !== 'undefined' && window.innerWidth >= CARD_GRID.LG_BREAKPOINT)

  useEffect(() => {
    const handleResize = () => {
      setIsDesktop(window.innerWidth >= CARD_GRID.LG_BREAKPOINT)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const scaledWidth = cardWidth * mobileScale
  const scaledHeight = cardHeight * mobileScale

  // Desktop: render without wrapper
  if (isDesktop) {
    return <div className={className}>{children}</div>
  }

  return (
    <div
      className={className}
      style={{
        width: `${scaledWidth}px`,
        height: `${scaledHeight}px`,
      }}
    >
      <div
        style={{
          transform: `scale(${mobileScale})`,
          transformOrigin: 'top left',
          width: `${cardWidth}px`,
          height: `${cardHeight}px`,
        }}
      >
        {children}
      </div>
    </div>
  )
}
