import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { FileText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface PlannerSectionProps {
  /** Section title - rendered as h2 */
  title: string
  /** Content inside the container */
  children: ReactNode
  /** Optional "View Notes" button handler */
  onViewNotes?: () => void
  /** Stretches the section and its container to the height of the row it sits in. */
  fill?: boolean
}

/**
 * Unified section wrapper for planner pages.
 * Provides consistent h2 header + bordered container styling.
 *
 * Uses SECTION_STYLES tokens for typography and container styling.
 * Optional "View Notes" button appears next to title when onViewNotes is provided.
 *
 * @example
 * <PlannerSection title={t('deckBuilder')} onViewNotes={() => setNotesOpen(true)}>
 *   <DeckBuilderContent />
 * </PlannerSection>
 */
export function PlannerSection({ title, children, onViewNotes, fill }: PlannerSectionProps) {
  const { t } = useTranslation('common')

  return (
    <section className={cn('mb-4', fill && 'h-full flex flex-col')}>
      <div className="flex items-center justify-between mb-2 h-8">
        <h2 className={SECTION_STYLES.TEXT.header}>{title}</h2>
        {onViewNotes && (
          <Button variant="ghost" size="sm" onClick={onViewNotes} className="gap-2">
            <FileText />
            {t('viewNotes')}
          </Button>
        )}
      </div>
      <div className={cn(SECTION_STYLES.container, fill && 'flex-1 min-h-0 flex flex-col')}>
        {children}
      </div>
    </section>
  )
}
