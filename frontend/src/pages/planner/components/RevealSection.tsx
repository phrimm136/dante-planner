import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

export interface RevealSectionSpec {
  id: string
  node: ReactNode
  aside?: ReactNode
}

export type RevealMode = 'mount' | 'fade'

interface RevealSectionProps {
  visible: boolean
  mode?: RevealMode
  children: ReactNode
}

export function RevealSection({ visible, mode = 'mount', children }: RevealSectionProps) {
  if (mode === 'fade') {
    return (
      <div className={cn('transition-opacity duration-200', visible ? 'opacity-100' : 'opacity-0')}>
        {children}
      </div>
    )
  }

  return visible ? <>{children}</> : null
}
