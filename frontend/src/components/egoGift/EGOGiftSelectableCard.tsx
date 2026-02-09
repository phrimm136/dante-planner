import { useState, memo, useEffect, useRef } from 'react'
import type { EnhancementLevel } from '@/lib/constants'
import { EGOGiftEnhancementSelector } from './EGOGiftEnhancementSelector'

interface EGOGiftSelectableCardProps {
  giftId: string
  enhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: string, enhancement: EnhancementLevel) => void
  children: React.ReactNode
}

// Inner component props (without children)
interface EGOGiftSelectableCardInnerProps {
  giftId: string
  enhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: string, enhancement: EnhancementLevel) => void
}

// Custom comparison for inner - exclude callback
function areInnerPropsEqual(
  prev: EGOGiftSelectableCardInnerProps,
  next: EGOGiftSelectableCardInnerProps
): boolean {
  return (
    prev.giftId === next.giftId &&
    prev.enhancement === next.enhancement &&
    prev.maxEnhancement === next.maxEnhancement &&
    prev.isSelected === next.isSelected
    // onEnhancementSelect excluded - stable behavior via functional update
  )
}

/**
 * Inner component that handles hover state and enhancement selector overlay
 * Separated from outer wrapper to prevent card re-renders on hover
 */
const EGOGiftSelectableCardInner = memo(function EGOGiftSelectableCardInner({
  giftId,
  enhancement,
  maxEnhancement,
  isSelected,
  onEnhancementSelect,
}: EGOGiftSelectableCardInnerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const isTouchDeviceRef = useRef(false)

  // Handle clicks outside to close on mobile
  useEffect(() => {
    if (!isOpen) return

    const handleClickOutside = (e: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false)
        isTouchDeviceRef.current = false
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('touchstart', handleClickOutside)

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('touchstart', handleClickOutside)
    }
  }, [isOpen])

  const handleTouchStart = (e: React.TouchEvent) => {
    e.stopPropagation()
    isTouchDeviceRef.current = true
    setIsOpen(true)
  }

  const handleMouseEnter = () => {
    if (!isTouchDeviceRef.current) {
      setIsOpen(true)
    }
  }

  const handleMouseLeave = () => {
    if (!isTouchDeviceRef.current) {
      setIsOpen(false)
    }
  }

  return (
    <div
      ref={containerRef}
      className="absolute inset-0"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onTouchStart={handleTouchStart}
    >
      {isOpen && (
        <EGOGiftEnhancementSelector
          giftId={giftId}
          currentEnhancement={enhancement}
          maxEnhancement={maxEnhancement}
          isSelected={isSelected}
          onSelect={onEnhancementSelect}
        />
      )}
    </div>
  )
}, areInnerPropsEqual)

// Custom comparison for outer wrapper - ignore children and callback
function arePropsEqual(prev: EGOGiftSelectableCardProps, next: EGOGiftSelectableCardProps): boolean {
  return (
    prev.giftId === next.giftId &&
    prev.enhancement === next.enhancement &&
    prev.maxEnhancement === next.maxEnhancement &&
    prev.isSelected === next.isSelected
    // children intentionally excluded
    // onEnhancementSelect excluded - callback identity may change but behavior is same
  )
}

/**
 * Gift card with enhancement selector overlay (for comprehensive list)
 * Shows enhancement selector on hover without re-rendering the card itself
 *
 * Pattern: Like TierLevelSelector
 * - Outer wrapper: memo with custom comparison excluding children
 * - Children rendered outside hover component with pointer-events-none
 * - Inner component: handles hover state + overlay separately
 * - Result: Hover only re-renders overlay, not the card
 */
export const EGOGiftSelectableCard = memo(function EGOGiftSelectableCard({
  giftId,
  enhancement,
  maxEnhancement,
  isSelected,
  onEnhancementSelect,
  children,
}: EGOGiftSelectableCardProps) {
  return (
    <div className="relative inline-block cursor-pointer">
      <div className="pointer-events-none">
        {children}
      </div>
      <EGOGiftSelectableCardInner
        giftId={giftId}
        enhancement={enhancement}
        maxEnhancement={maxEnhancement}
        isSelected={isSelected}
        onEnhancementSelect={onEnhancementSelect}
      />
    </div>
  )
}, arePropsEqual)
