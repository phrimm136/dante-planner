import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { FallbackImage } from '@/components/ui/FallbackImage'
import { Skeleton } from '@/components/ui/skeleton'
import { getButtonSwapImagePath } from '@/shared/assets'
import { OverlayButton } from './OverlayButton'

/**
 * The lightbox carries a pan/zoom engine and a dialog, which the placeholder branch and
 * every skeleton that draws this box would otherwise ship without ever opening it.
 */
const ExpandImageButton = lazy(async () => {
  const module = await import('./ExpandImageButton')

  return { default: module.ExpandImageButton }
})

interface CharacterImageSectionProps {
  /** Omitted → the box is drawn as a placeholder */
  src?: string | undefined
  /** Required alongside `src`; unused by the placeholder */
  alt?: string | undefined
  /** CSS aspect-ratio holding the box open while the image loads. */
  aspectRatio: string
  /** Rendered when `src` fails to load. */
  fallbackSrc?: string | undefined
  /** Fired on the fallback swap, for callers that mirror the resolved source. */
  onFallback?: (() => void) | undefined
  /** Renders a swap button above the expand button. */
  swap?: { onSwap: () => void; disabled?: boolean } | undefined
}

/**
 * Character image panel shared by the identity and EGO detail headers:
 * the image itself (with optional fallback) and its overlay buttons.
 *
 * Without a `src` it is the placeholder the detail skeletons draw.
 */
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
