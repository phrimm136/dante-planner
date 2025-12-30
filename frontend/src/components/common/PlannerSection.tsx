import type { ReactNode } from 'react'
import { SECTION_STYLES } from '@/lib/constants'

interface PlannerSectionProps {
  /** Section title - rendered as h2 */
  title: string
  /** Content inside the container */
  children: ReactNode
}

/**
 * Unified section wrapper for planner pages.
 * Provides consistent h2 header + bordered container styling.
 *
 * Uses SECTION_STYLES tokens for typography and container styling.
 * Intentionally minimal API: only title and children.
 *
 * @example
 * <PlannerSection title={t('deckBuilder')}>
 *   <DeckBuilderContent />
 * </PlannerSection>
 */
export function PlannerSection({ title, children }: PlannerSectionProps) {
  return (
    <section className={SECTION_STYLES.SPACING.content}>
      <h2 className={SECTION_STYLES.TEXT.header}>{title}</h2>
      <div className={SECTION_STYLES.container}>{children}</div>
    </section>
  )
}
