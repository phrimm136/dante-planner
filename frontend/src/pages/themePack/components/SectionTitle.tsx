import type { ReactNode } from 'react'

/**
 * Section header for the theme pack detail page's right column.
 */
export function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide border-b border-border pb-2">
      {children}
    </h2>
  )
}
