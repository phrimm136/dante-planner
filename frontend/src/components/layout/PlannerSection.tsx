import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { FileText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface PlannerSectionProps {
  title: string
  children: ReactNode
  onViewNotes?: () => void
  fill?: boolean
}

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
