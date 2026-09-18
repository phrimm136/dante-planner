import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { FallbackImage } from '@/components/ui/FallbackImage'
import { Skeleton } from '@/components/ui/skeleton'
import { getButtonSwapImagePath } from '@/shared/assets'
import { OverlayButton } from './OverlayButton'

const ExpandImageButton = lazy(async () => {
  const module = await import('./ExpandImageButton')

  return { default: module.ExpandImageButton }
})

interface CharacterImageSectionProps {
  src?: string | undefined
  alt?: string | undefined
  aspectRatio: string
  fallbackSrc?: string | undefined
  onFallback?: (() => void) | undefined
  swap?: { onSwap: () => void; disabled?: boolean } | undefined
}

export function CharacterImageSection({
  src,
  alt = '',
  aspectRatio,
  fallbackSrc,
  onFallback,
  swap,
}: CharacterImageSectionProps) {
  const { t } = useTranslation()

  if (src === undefined) {
    return (
      <div className="relative bg-muted rounded-lg overflow-hidden">
        <Skeleton className="w-full" style={{ aspectRatio }} />
      </div>
    )
  }

  return (
    <div className="relative bg-muted rounded-lg overflow-hidden">
      {fallbackSrc ? (
        <FallbackImage
          src={src}
          fallbackSrc={fallbackSrc}
          {...(onFallback !== undefined && { onFallback })}
          alt={alt}
          className="w-full h-auto object-contain"
          style={{ aspectRatio }}
        />
      ) : (
        <img src={src} alt={alt} className="w-full h-auto object-contain" style={{ aspectRatio }} />
      )}

      <div className="absolute top-4 left-4 flex flex-col gap-2">
        {swap && (
          <OverlayButton
            onClick={swap.onSwap}
            disabled={swap.disabled}
            iconSrc={getButtonSwapImagePath()}
            iconAlt={t('a11y.swapImage')}
          />
        )}
        <Suspense fallback={null}>
          <ExpandImageButton src={src} alt={alt} />
        </Suspense>
      </div>
    </div>
  )
}
