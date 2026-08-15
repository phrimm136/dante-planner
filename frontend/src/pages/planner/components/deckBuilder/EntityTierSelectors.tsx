import React, { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { MAX_LEVEL } from '@/shared/gameData'
import type { UptieTier, ThreadspinTier } from '../../types/DeckTypes'
import { getEGOTierIconPath } from '@/shared/assets'

const UPTIE_TIERS: UptieTier[] = [1, 2, 3, 4]

/** Distance before the viewport at which a selector starts mounting. */
const LAZY_MOUNT_MARGIN = '100px'

/** Base EGOs cannot be unequipped; their ids end in the first slot. */
const BASE_EGO_ID_SUFFIX = '01'

/**
 * What a confirmed selection reports. Identity selectors fill uptie and level,
 * EGO selectors fill threadspin.
 */
export interface TierSelection {
  uptie?: UptieTier
  threadspin?: ThreadspinTier
  level?: number
}

/**
 * Hover/touch shell shared by both selectors: it owns open state, the
 * outside-click dismissal, and the absolutely positioned popover.
 */
function HoverSelectorShell({ children }: { children: React.ReactNode }) {
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
        <div className="absolute z-50 top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-auto">
          <div className="flex flex-col items-center gap-2">{children}</div>
        </div>
      )}
    </div>
  )
}

interface TierRowProps<T extends number> {
  tiers: readonly T[]
  selectedTier: T
  onSelect: (tier: T) => void
}

function TierRow<T extends number>({ tiers, selectedTier, onSelect }: TierRowProps<T>) {
  return (
    <div className="flex bg-black/70 rounded px-2 py-1 justify-center">
      {tiers.map((tier) => (
        <button
          key={tier}
          type="button"
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            onSelect(tier)
          }}
          className="selectable w-8 h-8 cursor-pointer"
          data-selected={tier === selectedTier}
        >
          <img
            src={getEGOTierIconPath(tier)}
            alt={`Tier ${tier}`}
            className="w-full h-full object-contain"
          />
        </button>
      ))}
    </div>
  )
}

function ConfirmButton({
  label,
  onClick,
}: {
  label: string
  onClick: (e: React.MouseEvent) => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="px-3 py-0.5 text-xs bg-primary text-primary-foreground rounded hover:bg-primary/90"
    >
      {label}
    </button>
  )
}

interface LazySelectorProps {
  children: React.ReactNode
  /** Rendered once the card scrolls near the viewport. */
  selector: React.ReactNode
}

/**
 * Defers mounting the selector until its card approaches the viewport; a full
 * deck grid would otherwise mount hundreds of popovers up front.
 */
function LazySelector({ children, selector }: LazySelectorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [isVisible, setIsVisible] = useState(false)

  useEffect(() => {
    const element = containerRef.current
    if (!element) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          setIsVisible(true)
          observer.disconnect() // Once visible, stop observing
        }
      },
      { rootMargin: LAZY_MOUNT_MARGIN },
    )

    observer.observe(element)

    return () => {
      observer.disconnect()
    }
  }, [])

  return (
    <div ref={containerRef} className="relative inline-block group">
      <div className="pointer-events-none">{children}</div>
      {isVisible && selector}
    </div>
  )
}

interface IdentityTierSelectorProps {
  entityId: string
  currentUptie?: UptieTier
  currentLevel?: number
  onConfirm: (entityId: string, data: TierSelection) => void
  children: React.ReactNode
}

/**
 * Uptie and level picker overlaid on an identity card.
 */
export function IdentityTierSelector({
  entityId,
  currentUptie = 4,
  currentLevel = MAX_LEVEL,
  onConfirm,
  children,
}: IdentityTierSelectorProps) {
  return (
    <LazySelector
      selector={
        <IdentityTierSelectorInner
          entityId={entityId}
          currentUptie={currentUptie}
          currentLevel={currentLevel}
          onConfirm={onConfirm}
        />
      }
    >
      {children}
    </LazySelector>
  )
}

function IdentityTierSelectorInner({
  entityId,
  currentUptie,
  currentLevel,
  onConfirm,
}: {
  entityId: string
  currentUptie: UptieTier
  currentLevel: number
  onConfirm: (entityId: string, data: TierSelection) => void
}) {
  const { t } = useTranslation(['common'])
  const [uptie, setUptie] = useState<UptieTier>(currentUptie)
  const [level, setLevel] = useState<number>(currentLevel)

  const handleLevelChange = (value: string) => {
    const num = parseInt(value, 10)
    if (!isNaN(num)) {
      setLevel(Math.max(1, Math.min(MAX_LEVEL, num)))
    }
  }

  return (
    <HoverSelectorShell>
      <TierRow tiers={UPTIE_TIERS} selectedTier={uptie} onSelect={setUptie} />

      <div className="flex items-center bg-black/70 rounded">
        <input
          type="number"
          value={level}
          onChange={(e) => {
            handleLevelChange(e.target.value)
          }}
          onClick={(e) => {
            e.stopPropagation()
          }}
          min={1}
          max={MAX_LEVEL}
          className="w-10 h-5 px-1 text-center text-xs bg-transparent text-white border-0 outline-none"
        />
      </div>

      <ConfirmButton
        label={t('common:equip')}
        onClick={(e) => {
          e.preventDefault()
          e.stopPropagation()
          onConfirm(entityId, { uptie, level })
        }}
      />
    </HoverSelectorShell>
  )
}

interface EgoThreadspinSelectorProps {
  entityId: string
  currentThreadspin?: ThreadspinTier
  /** Per-EGO threadspin ceiling (4 or 5). */
  maxThreadspin?: ThreadspinTier
  isSelected?: boolean
  onConfirm: (entityId: string, data: TierSelection) => void
  onUnequip?: (entityId: string) => void
  children: React.ReactNode
}

/**
 * Threadspin picker overlaid on an EGO card. An already-equipped EGO at its
 * current threadspin offers unequip instead of equip.
 */
export function EgoThreadspinSelector({
  entityId,
  currentThreadspin = 4,
  maxThreadspin = 4,
  isSelected = false,
  onConfirm,
  onUnequip,
  children,
}: EgoThreadspinSelectorProps) {
  return (
    <LazySelector
      selector={
        <EgoThreadspinSelectorInner
          entityId={entityId}
          currentThreadspin={currentThreadspin}
          maxThreadspin={maxThreadspin}
          isSelected={isSelected}
          onConfirm={onConfirm}
          onUnequip={onUnequip}
        />
      }
    >
      {children}
    </LazySelector>
  )
}

function EgoThreadspinSelectorInner({
  entityId,
  currentThreadspin,
  maxThreadspin,
  isSelected,
  onConfirm,
  onUnequip,
}: {
  entityId: string
  currentThreadspin: ThreadspinTier
  maxThreadspin: ThreadspinTier
  isSelected: boolean
  onConfirm: (entityId: string, data: TierSelection) => void
  onUnequip?: ((entityId: string) => void) | undefined
}) {
  const { t } = useTranslation(['common'])
  const [threadspin, setThreadspin] = useState<ThreadspinTier>(currentThreadspin)

  const threadspinTiers = Array.from({ length: maxThreadspin }, (_, i) => (i + 1) as ThreadspinTier)

  const canUnequip =
    isSelected &&
    threadspin === currentThreadspin &&
    !entityId.endsWith(BASE_EGO_ID_SUFFIX) &&
    !!onUnequip

  return (
    <HoverSelectorShell>
      <TierRow tiers={threadspinTiers} selectedTier={threadspin} onSelect={setThreadspin} />

      {canUnequip ? (
        <ConfirmButton
          label={t('common:unequip')}
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            onUnequip?.(entityId)
          }}
        />
      ) : (
        <ConfirmButton
          label={t('common:equip')}
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            onConfirm(entityId, { threadspin })
          }}
        />
      )}
    </HoverSelectorShell>
  )
}
