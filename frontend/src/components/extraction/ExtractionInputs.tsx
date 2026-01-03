/**
 * Extraction Calculator Input Controls
 *
 * Input section for configuring banner settings:
 * - Number of pulls
 * - Featured item counts (3-star IDs, EGO, Announcers)
 * - Target item counts (how many of each you want)
 * - "All EGO collected" modifier
 *
 * Real-time updates via onChange - no submit button.
 *
 * @see ExtractionCalculator.tsx for state management
 * @see extractionCalculator.ts for calculation logic
 */

import { useTranslation } from 'react-i18next'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { SECTION_STYLES, EXTRACTION_RATES } from '@/lib/constants'
import { cn } from '@/lib/utils'

/** Input limits - centralized for maintainability */
const INPUT_LIMITS = {
  PULLS: { MIN: 0, MAX: 2000 },
  FEATURED_ID: { MIN: 1, MAX: 10 },
  WANTED_ID: { MIN: 0 },
  FEATURED_EGO: { MIN: 0, MAX: 10 },
  FEATURED_ANNOUNCER: { MIN: 0, MAX: 5 },
} as const

interface ExtractionInputsProps {
  /** Number of planned pulls */
  pulls: number
  /** Number of featured 3-star IDs on banner */
  featuredIds: number
  /** Number of 3-star IDs user wants */
  wantedIds: number
  /** Number of featured EGO on banner */
  featuredEgos: number
  /** Number of EGO user wants */
  wantedEgos: number
  /** Number of featured Announcers on banner */
  featuredAnnouncers: number
  /** Number of Announcers user wants */
  wantedAnnouncers: number
  /** User has collected all EGO from banner */
  allEgoCollected: boolean
  /** Current pity counter */
  currentPity: number
  /** Callback when pulls changes */
  onPullsChange: (value: number) => void
  /** Callback when featured IDs count changes */
  onFeaturedIdsChange: (value: number) => void
  /** Callback when wanted IDs count changes */
  onWantedIdsChange: (value: number) => void
  /** Callback when featured EGO count changes */
  onFeaturedEgosChange: (value: number) => void
  /** Callback when wanted EGO count changes */
  onWantedEgosChange: (value: number) => void
  /** Callback when featured Announcer count changes */
  onFeaturedAnnouncersChange: (value: number) => void
  /** Callback when wanted Announcer count changes */
  onWantedAnnouncersChange: (value: number) => void
  /** Callback when allEgoCollected toggle changes */
  onAllEgoCollectedChange: (value: boolean) => void
  /** Callback when current pity changes */
  onCurrentPityChange: (value: number) => void
}

/**
 * Input field group with label
 * Wraps Input with consistent Label styling
 */
function InputField({
  label,
  value,
  onChange,
  min,
  max,
  disabled = false,
}: {
  label: string
  value: number
  onChange: (value: number) => void
  min: number
  max?: number
  disabled?: boolean
}) {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const rawValue = e.target.value
    if (rawValue === '') {
      onChange(min)
      return
    }
    const parsed = parseInt(rawValue, 10)
    if (isNaN(parsed)) {
      return
    }
    // Clamp to valid range
    const clamped = Math.max(min, max !== undefined ? Math.min(max, parsed) : parsed)
    onChange(clamped)
  }

  return (
    <div className={SECTION_STYLES.SPACING.elements}>
      <Label className={SECTION_STYLES.TEXT.label}>{label}</Label>
      <Input
        type="number"
        value={value}
        onChange={handleChange}
        min={min}
        max={max}
        disabled={disabled}
        className="w-full max-w-[200px]"
      />
    </div>
  )
}

/**
 * Checkbox field with label
 * Custom checkbox since shadcn/ui checkbox not available
 */
function CheckboxField({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <label className="flex items-center gap-2 cursor-pointer">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="w-4 h-4 rounded border-border accent-primary"
      />
      <span className={SECTION_STYLES.TEXT.label}>{label}</span>
    </label>
  )
}

export function ExtractionInputs({
  pulls,
  featuredIds,
  wantedIds,
  featuredEgos,
  wantedEgos,
  featuredAnnouncers,
  wantedAnnouncers,
  allEgoCollected,
  currentPity,
  onPullsChange,
  onFeaturedIdsChange,
  onWantedIdsChange,
  onFeaturedEgosChange,
  onWantedEgosChange,
  onFeaturedAnnouncersChange,
  onWantedAnnouncersChange,
  onAllEgoCollectedChange,
  onCurrentPityChange,
}: ExtractionInputsProps) {
  const { t } = useTranslation('extraction')

  return (
    <div className={cn(SECTION_STYLES.container, 'space-y-6')}>
      {/* Pull Configuration */}
      <div className="space-y-4">
        <h3 className={SECTION_STYLES.TEXT.subHeader}>
          {t('inputs.pullConfig')}
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <InputField
            label={t('inputs.plannedPulls')}
            value={pulls}
            onChange={onPullsChange}
            min={INPUT_LIMITS.PULLS.MIN}
            max={INPUT_LIMITS.PULLS.MAX}
          />
          <InputField
            label={t('inputs.currentPity')}
            value={currentPity}
            onChange={onCurrentPityChange}
            min={0}
            max={EXTRACTION_RATES.PITY_PULLS - 1}
          />
        </div>
      </div>

      {/* Banner Configuration */}
      <div className="space-y-4">
        <h3 className={SECTION_STYLES.TEXT.subHeader}>
          {t('inputs.bannerConfig')}
        </h3>

        {/* 3-Star Identity */}
        <div className="space-y-2">
          <h4 className={SECTION_STYLES.TEXT.label}>
            {t('inputs.threeStarId')}
          </h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <InputField
              label={t('inputs.featured')}
              value={featuredIds}
              onChange={onFeaturedIdsChange}
              min={INPUT_LIMITS.FEATURED_ID.MIN}
              max={INPUT_LIMITS.FEATURED_ID.MAX}
            />
            <InputField
              label={t('inputs.wanted')}
              value={wantedIds}
              onChange={onWantedIdsChange}
              min={INPUT_LIMITS.WANTED_ID.MIN}
              max={featuredIds}
            />
          </div>
        </div>

        {/* EGO */}
        <div className="space-y-2">
          <h4 className={SECTION_STYLES.TEXT.label}>
            {t('inputs.ego')}
          </h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <InputField
              label={t('inputs.featured')}
              value={featuredEgos}
              onChange={onFeaturedEgosChange}
              min={INPUT_LIMITS.FEATURED_EGO.MIN}
              max={INPUT_LIMITS.FEATURED_EGO.MAX}
            />
            <InputField
              label={t('inputs.wanted')}
              value={wantedEgos}
              onChange={onWantedEgosChange}
              min={0}
              max={featuredEgos}
            />
          </div>
          {/* All EGO Collected = all NON-RATE-UP EGO owned, rate-up EGO still targetable */}
          <CheckboxField
            label={t('inputs.allEgoCollected')}
            checked={allEgoCollected}
            onChange={onAllEgoCollectedChange}
          />
        </div>

        {/* Announcer */}
        <div className="space-y-2">
          <h4 className={SECTION_STYLES.TEXT.label}>
            {t('inputs.announcer')}
          </h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <InputField
              label={t('inputs.featured')}
              value={featuredAnnouncers}
              onChange={onFeaturedAnnouncersChange}
              min={INPUT_LIMITS.FEATURED_ANNOUNCER.MIN}
              max={INPUT_LIMITS.FEATURED_ANNOUNCER.MAX}
            />
            <InputField
              label={t('inputs.wanted')}
              value={wantedAnnouncers}
              onChange={onWantedAnnouncersChange}
              min={0}
              max={featuredAnnouncers}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
