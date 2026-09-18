import { useState, useEffect, useRef } from 'react'
import type { EGOGiftId, EnhancementLevel } from '@/shared/gameData'
import { EGOGiftEnhancementSelector } from './EGOGiftEnhancementSelector'

interface EGOGiftSelectableCardProps {
  giftId: EGOGiftId
  enhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: EGOGiftId, enhancement: EnhancementLevel) => void
  children: React.ReactNode
}

interface EGOGiftSelectableCardInnerProps {
  giftId: EGOGiftId
  enhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: EGOGiftId, enhancement: EnhancementLevel) => void
}

const EGOGiftSelectableCardInner = function EGOGiftSelectableCardInner({
  giftId,
  enhancement,
  maxEnhancement,
  isSelected,
  onEnhancementSelect,
}: EGOGiftSelectableCardInnerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const isTouchDeviceRef = useRef(false)

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
}

export const EGOGiftSelectableCard = function EGOGiftSelectableCard({
  giftId,
  enhancement,
  maxEnhancement,
  isSelected,
  onEnhancementSelect,
  children,
}: EGOGiftSelectableCardProps) {
  return (
    <div className="relative w-full cursor-pointer">
      <div className="pointer-events-none">{children}</div>
      <EGOGiftSelectableCardInner
        giftId={giftId}
        enhancement={enhancement}
        maxEnhancement={maxEnhancement}
        isSelected={isSelected}
        onEnhancementSelect={onEnhancementSelect}
      />
    </div>
  )
}
