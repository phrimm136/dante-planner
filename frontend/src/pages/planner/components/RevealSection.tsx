import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * One entry of a surface's section array. The array index is the section's slot
 * in the progressive reveal, which is why its order is load-bearing.
 */
export interface RevealSectionSpec {
  /** React key of the entry. */
  id: string
  /** The section body, mounted once its slot is revealed. */
  node: ReactNode
  /** Sibling rendered outside the reveal wrapper, for a pane the reveal must not fade. */
  aside?: ReactNode
}

/** How a section arrives once its slot is revealed. */
export type RevealMode = 'mount' | 'fade'

interface RevealSectionProps {
  visible: boolean
  /** `mount` renders the section only once revealed; `fade` keeps it mounted and animates its opacity. */
  mode?: RevealMode
  children: ReactNode
}

/** Applies a surface's reveal mechanism to one section. */
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
