import { useState } from 'react'

import type { ComponentProps } from 'react'

interface FallbackImageProps extends Omit<ComponentProps<'img'>, 'src' | 'onError'> {
  src: string
  /** Rendered once `src` fails to load. */
  fallbackSrc: string
  /** Fired on the swap, for callers that mirror the resolved source in their own state. */
  onFallback?: () => void
}

/**
 * `<img>` that swaps to `fallbackSrc` when `src` fails to load. The swap happens
 * at most once per `src`, so a failing fallback cannot loop, and a new `src`
 * gets its own attempt.
 */
export function FallbackImage({ src, fallbackSrc, onFallback, ...props }: FallbackImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const hasFailed = failedSrc === src

  return (
    <img
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
