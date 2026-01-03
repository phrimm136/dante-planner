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
import { FormattedKeyword } from '@/components/common/FormattedKeyword'
import { useKeywordFormatter } from '@/hooks/useKeywordFormatter'
import { DIFFICULTY_BADGE_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface EGOGiftMetadataProps {
  /** Gift keyword (e.g., "Combustion") or null */
  keyword: string | null
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
  keyword,
  price,
  themePack,
  themePackNames,
  hardOnly,
  extremeOnly,
}: EGOGiftMetadataProps) {
  const { t } = useTranslation()
  const { resolve } = useKeywordFormatter()

  // Resolve theme pack names, show "General" if empty
  const themePackDisplay =
    themePack.length > 0
      ? themePack.map((id) => themePackNames[id]?.name ?? id).join(', ')
      : t('egoGift.general', 'General')

  // Resolve keyword if present
  const resolvedKeyword = keyword ? resolve(keyword) : null

  return (
    <div className="border rounded p-4 space-y-4">
      {/* Keyword row - only show if keyword exists */}
      {resolvedKeyword && (
        <MetadataRow label={t('egoGift.keyword', 'Keyword')}>
          <FormattedKeyword keyword={resolvedKeyword} />
        </MetadataRow>
      )}

      {/* Price row */}
      <MetadataRow label={t('egoGift.price', 'Price')}>
        <CostDisplay cost={price} />
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
