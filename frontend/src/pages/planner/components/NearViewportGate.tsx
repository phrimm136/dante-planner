import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

interface NearViewportGateProps {
  placeholder: ReactNode
  children: ReactNode
}

export function NearViewportGate({ placeholder, children }: NearViewportGateProps) {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [isNear, setIsNear] = useState(false)

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || isNear) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          setIsNear(true)
        }
      },
      { rootMargin: `${window.innerHeight}px 0px` },
    )

    observer.observe(sentinel)

    return () => {
      observer.disconnect()
    }
  }, [isNear])

  return <div ref={sentinelRef}>{isNear ? children : placeholder}</div>
}
