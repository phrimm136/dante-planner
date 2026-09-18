import { useTranslation } from 'react-i18next'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { SECTION_STYLES } from '@/lib/constants'
import { EXTRACTION_RATES } from '../lib/extractionRates'
import { cn } from '@/lib/utils'

const INPUT_LIMITS = {
  PULLS: { MIN: 0, MAX: 2000 },
  FEATURED_ID: { MIN: 0, MAX: 10 },
  WANTED_ID: { MIN: 0 },
  FEATURED_EGO: { MIN: 0, MAX: 10 },
  FEATURED_ANNOUNCER: { MIN: 0, MAX: 10 },
} as const

interface ExtractionInputsProps {
  pulls: number
  featuredIds: number
  wantedIds: number
  featuredEgos: number
  wantedEgos: number
  featuredAnnouncers: number
  wantedAnnouncers: number
  allEgoCollected: boolean
  currentPity: number
  onPullsChange: (value: number) => void
  onFeaturedIdsChange: (value: number) => void
  onWantedIdsChange: (value: number) => void
  onFeaturedEgosChange: (value: number) => void
  onWantedEgosChange: (value: number) => void
  onFeaturedAnnouncersChange: (value: number) => void
  onWantedAnnouncersChange: (value: number) => void
  onAllEgoCollectedChange: (value: boolean) => void
  onCurrentPityChange: (value: number) => void
}

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
      <div className="space-y-4">
        <h3 className={SECTION_STYLES.TEXT.subHeader}>{t('inputs.pullConfig')}</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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

      <div className="space-y-4">
        <h3 className={SECTION_STYLES.TEXT.subHeader}>{t('inputs.bannerConfig')}</h3>

        <div className="space-y-2">
          <h4 className={SECTION_STYLES.TEXT.label}>{t('inputs.threeStarId')}</h4>
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

        <div className="space-y-2">
          <h4 className={SECTION_STYLES.TEXT.label}>{t('inputs.ego')}</h4>
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
          <CheckboxField
            label={t('inputs.allEgoCollected')}
            checked={allEgoCollected}
            onChange={onAllEgoCollectedChange}
          />
        </div>

        <div className="space-y-2">
          <h4 className={SECTION_STYLES.TEXT.label}>{t('inputs.announcer')}</h4>
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
