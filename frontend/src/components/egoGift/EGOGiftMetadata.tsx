/**
 * EGOGiftMetadata - Vertical metadata display for EGO Gift detail page
 *
 * Displays gift metadata in a vertical stack layout:
 * - Keyword (with FormattedKeyword if available)
 * - Price (with coin icon)
 * - Theme Pack (names or "General")
 * - Difficulty (Hard/Extreme badges if applicable)
 *
 * Pattern Source: StatusPanel.tsx (vertical label-value structure)
 */

import { useTranslation } from 'react-i18next'

import CostDisplay from '@/components/egoGift/CostDisplay'
import { getEGOGiftEnhancementIconPath } from '@/lib/assetPaths'
import { DIFFICULTY_BADGE_STYLES, ENHANCEMENT_LABELS, type EnhancementLevel } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface EGOGiftMetadataProps {
  /** Gift price */
  price: number
  /** Theme pack IDs */
  themePack: string[]
  /** Resolved theme pack names map (i18n entry with name and optional specialName) */
  themePackNames: Record<string, { name: string; specialName?: string }>
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

export function EGOGiftMetadata({
  price,
  themePack,
  themePackNames,
  hardOnly,
  extremeOnly,
  maxEnhancement,
}: EGOGiftMetadataProps) {
  const { t } = useTranslation()

  // Resolve theme pack names, show "General" if empty
  const themePackDisplay =
    themePack.length > 0
      ? themePack.map((id) => themePackNames[id]?.name ?? id).join(', ')
      : t('egoGift.general', 'General')

  return (
    <div className="border rounded p-4 space-y-4">
      {/* Price row */}
      <MetadataRow label={t('egoGift.price', 'Price')}>
        <CostDisplay cost={price} />
      </MetadataRow>

      {/* Max Enhancement row */}
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

      {/* Theme Pack row */}
      <MetadataRow label={t('egoGift.themePack', 'Theme Pack')}>
        {themePackDisplay}
      </MetadataRow>

      {/* Difficulty row - only show if hardOnly or extremeOnly */}
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
