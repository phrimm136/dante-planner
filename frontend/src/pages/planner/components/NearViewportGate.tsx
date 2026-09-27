import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

interface NearViewportGateProps {
  placeholder: ReactNode
  children: ReactNode
  open?: boolean
}

export function NearViewportGate({ placeholder, children, open = false }: NearViewportGateProps) {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const [isNear, setIsNear] = useState(false)
  const isOpen = open || isNear

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || isOpen) return

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
  }, [isOpen])

  return <div ref={sentinelRef}>{isOpen ? children : placeholder}</div>
}
