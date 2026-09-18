import { useState } from 'react'

import type { ComponentProps } from 'react'

interface FallbackImageProps extends Omit<ComponentProps<'img'>, 'src' | 'onError' | 'alt'> {
  src: string
  alt: string
  fallbackSrc: string
  onFallback?: () => void
}

export function FallbackImage({ src, alt, fallbackSrc, onFallback, ...props }: FallbackImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const hasFailed = failedSrc === src

  return (
    <img
      alt={alt}
      {...props}
      src={hasFailed ? fallbackSrc : src}
      onError={() => {
        if (hasFailed || src === fallbackSrc) return
        setFailedSrc(src)
        onFallback?.()
      }}
    />
  )
}
