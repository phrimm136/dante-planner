import { Suspense } from 'react'
import { ErrorBoundary } from 'react-error-boundary'
import { useEGOGiftDetailData } from '@/hooks/useEGOGiftDetailData'
import { getColorForAttributeType, useColorCodes } from '@/hooks/useColorCodes'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import type { EnhancementLevel } from '@/lib/constants'

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
  const { spec, i18n: giftI18n } = useEGOGiftDetailData(giftId)
  const { data: colorCodes } = useColorCodes()
  const nameColor = getColorForAttributeType(colorCodes, spec.attributeType)
  const description = giftI18n.descs[enhancement]

  return (
    <>
      {/* Name with attribute color */}
      <p className="font-semibold mb-2" style={{ color: nameColor }}>
        {giftI18n.name}
      </p>

      {/* Description based on enhancement level */}
      {description ? (
        <div className="text-sm">
          <FormattedDescription text={description} />
        </div>
      ) : (
        <p className="text-sm">No description available</p>
      )}
    </>
  )
}

/**
 * Loading fallback for tooltip content
 */
function TooltipLoading() {
  return <p className="text-sm">Loading...</p>
}

/**
 * Error fallback for tooltip content - lightweight, no reset button
 */
function TooltipError() {
  return <p className="text-sm">Failed to load gift info</p>
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
