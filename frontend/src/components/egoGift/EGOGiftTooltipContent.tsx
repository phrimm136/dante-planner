import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary } from 'react-error-boundary'
import { useEGOGiftDetailData } from '@/hooks/useEGOGiftDetailData'
import { getColorForAttributeType, useColorCodes } from '@/hooks/useColorCodes'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import type { EnhancementLevel } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'

interface EGOGiftTooltipInnerProps {
  giftId: string
  enhancement: EnhancementLevel
}

/**
 * Inner component that fetches and displays gift tooltip content
 * Uses useEGOGiftDetailData which is Suspense-ready
 * Must be wrapped in Suspense boundary
 */
function EGOGiftTooltipInner({ giftId, enhancement }: EGOGiftTooltipInnerProps) {
  const { t, i18n } = useTranslation('common')
  const { spec, i18n: giftI18n } = useEGOGiftDetailData(giftId)
  const { data: colorCodes } = useColorCodes()
  const nameColor = getColorForAttributeType(colorCodes, spec.attributeType)
  const description = giftI18n.descs[enhancement]
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <>
      {/* Name with attribute color */}
      <p className="font-semibold text-[15px] mb-2" style={{ color: nameColor, ...displayStyle }}>
        {giftI18n.name}
      </p>

      {/* Description based on enhancement level */}
      {description ? (
        <div className="text-sm text-wrap break-keep">
          <FormattedDescription text={description} />
        </div>
      ) : (
        <p className="text-sm">{t('noDescription')}</p>
      )}
    </>
  )
}

/**
 * Loading fallback for tooltip content
 */
function TooltipLoading() {
  const { t } = useTranslation('common')
  return <p className="text-sm">{t('loading')}</p>
}

/**
 * Error fallback for tooltip content - lightweight, no reset button
 */
function TooltipError() {
  const { t } = useTranslation('common')
  return <p className="text-sm">{t('loadError')}</p>
}

interface EGOGiftTooltipContentProps {
  giftId: string
  enhancement: EnhancementLevel
}

/**
 * Tooltip content for EGO gift cards and enhancement buttons
 * Shows colored name + description with Suspense for lazy loading
 *
 * Usage:
 * - Observation list: Render on card hover
 * - Comprehensive list: Render on enhancement button hover
 */
export function EGOGiftTooltipContent({
  giftId,
  enhancement,
}: EGOGiftTooltipContentProps) {
  return (
    <ErrorBoundary fallback={<TooltipError />}>
      <Suspense fallback={<TooltipLoading />}>
        <EGOGiftTooltipInner giftId={giftId} enhancement={enhancement} />
      </Suspense>
    </ErrorBoundary>
  )
}
