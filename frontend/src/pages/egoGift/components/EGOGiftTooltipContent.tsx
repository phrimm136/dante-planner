import { Suspense, useRef, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { useEGOGiftDetailSpec, useEGOGiftDetailI18n } from '../hooks/useEGOGiftDetailData'
import { FormattedDescription } from '@/shared/gameText'
import { getAttributeColors } from '@/shared/gameData'
import type { EnhancementLevel } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'

interface EGOGiftTooltipInnerProps {
  giftId: string
  enhancement: EnhancementLevel
}

function EGOGiftTooltipInner({ giftId, enhancement }: EGOGiftTooltipInnerProps) {
  const { t, i18n } = useTranslation('common')
  const spec = useEGOGiftDetailSpec(giftId)
  const giftI18n = useEGOGiftDetailI18n(giftId)
  const { primary: nameColor } = getAttributeColors(spec.attributeType)
  const description = giftI18n.descs[enhancement]
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  const scrollRef = useRef<HTMLDivElement>(null)
  const [canScrollUp, setCanScrollUp] = useState(false)
  const [canScrollDown, setCanScrollDown] = useState(false)

  const updateScrollState = () => {
    const el = scrollRef.current
    if (!el) return
    const hasMoreAbove = el.scrollTop > 1
    const hasMoreBelow = el.scrollHeight - el.scrollTop - el.clientHeight > 1
    setCanScrollUp(hasMoreAbove)
    setCanScrollDown(hasMoreBelow)
  }

  useEffect(() => {
    updateScrollState()
  }, [description])

  return (
    <>
      <p className="font-semibold text-[15px] mb-2" style={{ color: nameColor, ...displayStyle }}>
        {giftI18n.name}
      </p>

      {description ? (
        <div className="relative h-[200px]">
          <div
            ref={scrollRef}
            className="text-sm text-wrap break-keep h-full overflow-y-auto scrollbar-hide"
            onScroll={updateScrollState}
            onWheel={(e) => {
              e.stopPropagation()
            }}
          >
            <FormattedDescription text={description} />
          </div>
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

function TooltipLoading() {
  const { t } = useTranslation('common')
  return <p className="text-sm">{t('loading')}</p>
}

function TooltipError() {
  const { t } = useTranslation('common')
  return <p className="text-sm">{t('loadError')}</p>
}

interface EGOGiftTooltipContentProps {
  giftId: string
  enhancement: EnhancementLevel
}

export function EGOGiftTooltipContent({ giftId, enhancement }: EGOGiftTooltipContentProps) {
  return (
    <ReactErrorBoundary fallback={<TooltipError />}>
      <Suspense fallback={<TooltipLoading />}>
        <EGOGiftTooltipInner giftId={giftId} enhancement={enhancement} />
      </Suspense>
    </ReactErrorBoundary>
  )
}
