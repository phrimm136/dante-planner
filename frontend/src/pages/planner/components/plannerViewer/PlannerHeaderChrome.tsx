import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { getKeywordIconPath } from '@/shared/assets'
import { MdCategoryLabel } from '../MdCategoryLabel'
import { categoryBadgeStyle } from '../../lib/plannerBadges'

import type { ReactNode } from 'react'
import { SECTION_STYLES } from '@/lib/constants'

interface PlannerHeaderChromeProps {
  onBack: () => void
  /** Sits between the back button and the category badge. */
  leading?: ReactNode
  category: string
  keywords: readonly string[]
  /** Right-hand side of the first row. */
  meta: ReactNode
  title: string
  /** Right-hand side of the title row. */
  actions: ReactNode
  /** Rows and dialogs specific to one variant. */
  children?: ReactNode
}

/**
 * The rows both planner detail headers share: back / category / keywords with a
 * variant-supplied meta block, then the title with variant-supplied actions.
 */
export function PlannerHeaderChrome({
  onBack,
  leading,
  category,
  keywords,
  meta,
  title,
  actions,
  children,
}: PlannerHeaderChromeProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <header className="space-y-3">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onBack}
            aria-label={t('pages.detail.backToList')}
            className="shrink-0"
          >
            <ArrowLeft className="size-4" />
          </Button>
          {leading}
          <span
            className="px-2 py-0.5 text-sm font-medium rounded shrink-0"
            style={categoryBadgeStyle(category)}
          >
            <MdCategoryLabel category={category} />
          </span>
          {keywords.length > 0 && (
            <div className="flex items-center gap-1.5 overflow-x-auto">
              {keywords.map((keyword) => (
                <img
                  key={keyword}
                  src={getKeywordIconPath(keyword)}
                  alt={keyword}
                  className="size-6 object-contain shrink-0"
                />
              ))}
            </div>
          )}
        </div>

        <div className="flex items-center gap-3 shrink-0">{meta}</div>
      </div>

      <div className="flex items-center justify-between gap-4">
        <h1 className={SECTION_STYLES.TEXT.pageTitle}>{title || t('untitled')}</h1>

        <div className="flex items-center gap-1 shrink-0">{actions}</div>
      </div>

      {children}
    </header>
  )
}
