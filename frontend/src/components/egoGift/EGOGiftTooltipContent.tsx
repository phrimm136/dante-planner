import { Suspense, useRef, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary } from 'react-error-boundary'
import { ChevronDown, ChevronUp } from 'lucide-react'
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

  const scrollRef = useRef<HTMLDivElement>(null)
  const [canScrollUp, setCanScrollUp] = useState(false)
  const [canScrollDown, setCanScrollDown] = useState(false)

  // Check if content is scrollable and update indicator state
  const updateScrollState = () => {
    const el = scrollRef.current
    if (!el) return
    const hasMoreAbove = el.scrollTop > 1
    const hasMoreBelow = el.scrollHeight - el.scrollTop - el.clientHeight > 1
    setCanScrollUp(hasMoreAbove)
    setCanScrollDown(hasMoreBelow)
  }

  // Initial check after render
  useEffect(() => {
    updateScrollState()
  }, [description])

  return (
    <>
      {/* Name with attribute color */}
      <p className="font-semibold text-[15px] mb-2" style={{ color: nameColor, ...displayStyle }}>
        {giftI18n.name}
      </p>

      {/* Description based on enhancement level */}
      {description ? (
        <div className="relative h-[200px]">
          <div
            ref={scrollRef}
            className="text-sm text-wrap break-keep h-full overflow-y-auto scrollbar-hide"
            onScroll={updateScrollState}
            onWheel={(e) => { e.stopPropagation() }}
          >
            <FormattedDescription text={description} />
          </div>
          {/* Scroll indicators - absolute positioned to avoid layout shift */}
          {canScrollUp && (
            <div className="absolute top-0 left-0 right-0 flex justify-center pointer-events-none">
              <ChevronUp className="w-4 h-4 text-muted-foreground animate-bounce" />
            </div>
          )}
          {canScrollDown && (
            <div className="absolute bottom-0 left-0 right-0 flex justify-center pointer-events-none">
              <ChevronDown className="w-4 h-4 text-muted-foreground animate-bounce" />
            </div>
          )}
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
