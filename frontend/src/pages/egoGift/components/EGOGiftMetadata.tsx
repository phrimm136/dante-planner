/**
 * EGOGiftMetadata - Vertical metadata display for EGO Gift detail page
 *
 * Displays gift metadata in a vertical stack layout with internal Suspense:
 * - Price (with coin icon) - always visible
 * - Max Enhancement (icon) - always visible
 * - Theme Pack (names or "General") - suspends for i18n
 * - Difficulty (Hard/Extreme badges) - always visible
 *
 * Pattern Source: TraitsDisplay.tsx (internal Suspense for i18n content)
 */

import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import CostDisplay from './CostDisplay'
import { getEGOGiftEnhancementIconPath } from '@/lib/assetPaths'
import { useThemePackI18n } from '@/pages/themePack'
import { DIFFICULTY_BADGE_STYLES, ENHANCEMENT_LABELS, type EnhancementLevel } from '@/lib/constants'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface EGOGiftMetadataProps {
  /** Gift price */
  price: number
  /** Theme pack IDs */
  themePack: string[]
  /** Whether gift is hard mode only */
  hardOnly?: boolean
  /** Whether gift is extreme mode only */
  extremeOnly?: boolean
  /** Maximum enhancement level (0 = base, 1 = +, 2 = ++) */
  maxEnhancement: EnhancementLevel
}

/**
 * Single metadata row with label and value
 */
function MetadataRow({
  label,
  children,
  className,
}: {
  label: string
  children: React.ReactNode
  className?: string
}) {
  return (
    <div className={cn('space-y-1', className)}>
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {label}
      </div>
      <div className="text-sm">{children}</div>
    </div>
  )
}

/**
 * Theme pack names display - suspends for i18n.
 * Renders comma-separated clickable links to theme pack detail pages.
 */
function ThemePackDisplay({ themePack }: { themePack: string[] }) {
  const { t } = useTranslation(['database', 'common'])
  const themePackI18n = useThemePackI18n()

  if (themePack.length === 0) {
    return <>{t('egoGift.general', 'General')}</>
  }

  return (
    <>
      {themePack.map((id, idx) => (
        <span key={id}>
          {idx > 0 && ', '}
          <Link
            to="/theme-pack/$id"
            params={{ id }}
            className="hover:underline text-foreground"
          >
            {themePackI18n[id]?.name ?? id}
          </Link>
        </span>
      ))}
    </>
  )
}

export function EGOGiftMetadata({
  price,
  themePack,
  hardOnly,
  extremeOnly,
  maxEnhancement,
}: EGOGiftMetadataProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <div className="border rounded p-4 space-y-4">
      {/* Price row - always visible */}
      <MetadataRow label={t('egoGift.price', 'Price')}>
        <CostDisplay cost={price} />
      </MetadataRow>

      {/* Max Enhancement row - always visible */}
      <MetadataRow label={t('egoGift.maxEnhancement', 'Max Enhancement')}>
        <div className="flex items-center gap-2">
          {maxEnhancement === 0 ? (
            <span className="text-sm font-medium">{ENHANCEMENT_LABELS[maxEnhancement]}</span>
          ) : (
            <img
              src={getEGOGiftEnhancementIconPath(maxEnhancement)}
              alt={ENHANCEMENT_LABELS[maxEnhancement]}
              className="w-6 h-6 object-contain"
            />
          )}
        </div>
      </MetadataRow>

      {/* Theme Pack row - label visible, content suspends */}
      <MetadataRow label={t('egoGift.relatedThemePack', 'Related Theme Pack')}>
        <Suspense fallback={<Skeleton className="h-4 w-24" />}>
          <ThemePackDisplay themePack={themePack} />
        </Suspense>
      </MetadataRow>

      {/* Difficulty row - always visible */}
      {(hardOnly || extremeOnly) && (
        <MetadataRow label={t('egoGift.difficulty', 'Difficulty')}>
          <div className="flex gap-2">
            {hardOnly && (
              <span className={cn('px-2 py-0.5 text-xs font-medium rounded', DIFFICULTY_BADGE_STYLES.HARD)}>
                {t('egoGift.hard', 'Hard')}
              </span>
            )}
            {extremeOnly && (
              <span className={cn('px-2 py-0.5 text-xs font-medium rounded', DIFFICULTY_BADGE_STYLES.EXTREME)}>
                {t('egoGift.extreme', 'Extreme')}
              </span>
            )}
          </div>
        </MetadataRow>
      )}
    </div>
  )
}

export default EGOGiftMetadata
