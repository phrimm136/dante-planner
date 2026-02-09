import React, { useState, memo, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { MAX_LEVEL } from '@/lib/constants'
import type { UptieTier, ThreadspinTier } from '@/types/DeckTypes'
import type { EGOType } from '@/types/EGOTypes'

interface TierLevelSelectorProps {
  mode: 'identity' | 'ego'
  entityId: string
  currentUptie?: UptieTier
  currentThreadspin?: ThreadspinTier
  currentLevel?: number
  isSelected?: boolean
  egoType?: EGOType
  onConfirm: (entityId: string, data: {
    uptie?: UptieTier
    threadspin?: ThreadspinTier
    level?: number
  }) => void
  onUnequip?: (entityId: string) => void
  children: React.ReactNode
}

const UPTIE_TIERS: UptieTier[] = [1, 2, 3, 4]
const THREADSPIN_TIERS: ThreadspinTier[] = [1, 2, 3, 4]

// Get tier icon path
const getTierIconPath = (tier: number) => `/images/UI/common/tier${tier}.webp`

// Inner component props (without children)
interface TierLevelSelectorInnerProps {
  mode: 'identity' | 'ego'
  entityId: string
  currentUptie: UptieTier
  currentThreadspin: ThreadspinTier
  currentLevel: number
  isSelected: boolean
  egoType?: EGOType
  onConfirm: (entityId: string, data: {
    uptie?: UptieTier
    threadspin?: ThreadspinTier
    level?: number
  }) => void
  onUnequip?: (entityId: string) => void
}

// Inner component that handles the actual tier/level selection UI
const TierLevelSelectorInner = memo(function TierLevelSelectorInner({
  mode,
  entityId,
  currentUptie,
  currentThreadspin,
  currentLevel,
  isSelected,
  onConfirm,
  onUnequip,
}: TierLevelSelectorInnerProps) {
  const { t } = useTranslation(['common'])
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const isTouchDeviceRef = useRef(false)

  const [uptie, setUptie] = useState<UptieTier>(currentUptie)
  const [threadspin, setThreadspin] = useState<ThreadspinTier>(currentThreadspin)
  const [level, setLevel] = useState<number>(currentLevel)

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

  const handleLevelChange = (value: string) => {
    const num = parseInt(value, 10)
    if (!isNaN(num)) {
      setLevel(Math.max(1, Math.min(MAX_LEVEL, num)))
    }
  }

  const handleConfirm = (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (mode === 'identity') {
      onConfirm(entityId, { uptie, level })
    } else {
      onConfirm(entityId, { threadspin })
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
        <div
          className="absolute z-50 top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-auto"
        >
          <div className="flex flex-col items-center gap-2">
            {/* Uptie/Threadspin tier icons with background */}
            <div className="flex bg-black/70 rounded px-2 py-1 justify-center">
              {(mode === 'identity' ? UPTIE_TIERS : THREADSPIN_TIERS).map((tier) => {
                const isSelected = mode === 'identity' ? uptie === tier : threadspin === tier
                return (
                  <button
                    key={tier}
                    type="button"
                    onClick={(e) => {
                      e.preventDefault()
                      e.stopPropagation()
                      if (mode === 'identity') {
                        setUptie(tier)
                      } else {
                        setThreadspin(tier as ThreadspinTier)
                      }
                    }}
                    className="selectable w-8 h-8 cursor-pointer"
                    data-selected={isSelected}
                  >
                    <img
                      src={getTierIconPath(tier)}
                      alt={`Tier ${tier}`}
                      className="w-full h-full object-contain"
                    />
                  </button>
                )
              })}
            </div>

            {/* Level input (Identity only) */}
            {mode === 'identity' && (
              <div className="flex items-center bg-black/70 rounded">
                <input
                  type="number"
                  value={level}
                  onChange={(e) => { handleLevelChange(e.target.value); }}
                  onClick={(e) => { e.stopPropagation(); }}
                  min={1}
                  max={MAX_LEVEL}
                  className="w-10 h-5 px-1 text-center text-xs bg-transparent text-white border-0 outline-none"
                />
              </div>
            )}

            {/* Equip/Unequip button */}
            {mode === 'ego' && isSelected && !entityId.endsWith('01') && onUnequip ? (
              <button
                type="button"
                onClick={(e) => {
                  e.preventDefault()
                  e.stopPropagation()
                  onUnequip(entityId)
                }}
                className="px-3 py-0.5 text-xs bg-primary text-primary-foreground rounded hover:bg-primary/90"
              >
                {t('common:unequip')}
              </button>
            ) : (
              <button
                type="button"
                onClick={handleConfirm}
                className="px-3 py-0.5 text-xs bg-primary text-primary-foreground rounded hover:bg-primary/90"
              >
                {t('common:equip')}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
})

// Custom comparison for outer wrapper - ignore children and callbacks
function arePropsEqual(prev: TierLevelSelectorProps, next: TierLevelSelectorProps): boolean {
  return (
    prev.mode === next.mode &&
    prev.entityId === next.entityId &&
    prev.currentUptie === next.currentUptie &&
    prev.currentThreadspin === next.currentThreadspin &&
    prev.currentLevel === next.currentLevel &&
    prev.isSelected === next.isSelected
    // egoType excluded - no longer used in logic, entityId determines base ego
    // children intentionally excluded - isSelected tracks selection state
    // onConfirm/onUnequip excluded - callback identity changes but behavior is same
  )
}

// Outer wrapper with lazy loading via IntersectionObserver
export const TierLevelSelector: React.FC<TierLevelSelectorProps> = memo(function TierLevelSelector({
  mode,
  entityId,
  currentUptie = 4,
  currentThreadspin = 4,
  currentLevel = MAX_LEVEL,
  isSelected = false,
  egoType,
  onConfirm,
  onUnequip,
  children,
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [isVisible, setIsVisible] = useState(false)

  useEffect(() => {
    const element = containerRef.current
    if (!element) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true)
          observer.disconnect() // Once visible, stop observing
        }
      },
      {
        rootMargin: '100px', // Start loading slightly before entering viewport
      }
    )

    observer.observe(element)

    return () => { observer.disconnect(); }
  }, [])

  return (
    <div ref={containerRef} className="relative inline-block group">
      <div className="pointer-events-none">
        {children}
      </div>
      {isVisible && (
        <TierLevelSelectorInner
          mode={mode}
          entityId={entityId}
          currentUptie={currentUptie}
          currentThreadspin={currentThreadspin}
          currentLevel={currentLevel}
          isSelected={isSelected}
          egoType={egoType}
          onConfirm={onConfirm}
          onUnequip={onUnequip}
        />
      )}
    </div>
  )
}, arePropsEqual)

export default TierLevelSelector
