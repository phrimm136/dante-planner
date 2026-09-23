import { useId, type ReactNode } from 'react'

import { Separator } from '@/components/ui/separator'
import { SECTION_STYLES } from '@/lib/constants'

interface DetailSectionProps {
  title: string
  children: ReactNode
}

export function DetailSection({ title, children }: DetailSectionProps) {
  const titleId = useId()

  return (
    <section aria-labelledby={titleId} className={SECTION_STYLES.SPACING.content}>
      <h2 id={titleId} className={SECTION_STYLES.TEXT.sectionTitle}>
        {title}
      </h2>
      <Separator />
      {children}
    </section>
  )
}
