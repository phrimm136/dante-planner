import { useTranslation } from 'react-i18next'

import { FallbackImage } from '@/components/ui/FallbackImage'
import { getButtonSwapImagePath } from '@/shared/assets'
import { ExpandImageButton } from './ExpandImageButton'
import { OverlayButton } from './OverlayButton'

interface CharacterImageSectionProps {
  src: string
  alt: string
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
 */
export function CharacterImageSection({
  src,
  alt,
  aspectRatio,
  fallbackSrc,
  onFallback,
  swap,
}: CharacterImageSectionProps) {
  const { t } = useTranslation()

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
        <ExpandImageButton src={src} alt={alt} />
      </div>
    </div>
  )
}
