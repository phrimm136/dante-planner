import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { getKeywordIconPath } from '@/shared/assets'
import { MdCategoryLabel } from '../MdCategoryLabel'
import { categoryBadgeStyle } from '../../lib/plannerBadges'

import type { ReactNode } from 'react'
import { SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface PlannerHeaderChromeProps {
  onBack: () => void
  leading?: ReactNode
  category: string
  keywords: readonly string[]
  meta: ReactNode
  title: string
  actions: ReactNode
  children?: ReactNode
}

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
        <h1
          className={cn(SECTION_STYLES.TEXT.pageTitle, 'min-w-0 wrap-anywhere text-xl lg:text-2xl')}
        >
          {title || t('untitled')}
        </h1>

        <div className="flex items-center gap-1 shrink-0">{actions}</div>
      </div>

      {children}
    </header>
  )
}
