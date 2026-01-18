import { useTranslation } from 'react-i18next'

import { MD_CATEGORIES } from '@/lib/constants'

import type { MDCategory } from '@/lib/constants'

interface PlannerListFilterPillsProps {
  /** Currently selected category (undefined = All) */
  selectedCategory: MDCategory | undefined
  /** Callback when category changes */
  onCategoryChange: (category: MDCategory | undefined) => void
}

/**
 * Inline category filter pills for planner list.
 * Shows: All, 5F, 10F, 15F
 *
 * @example
 * const { category, setFilters } = usePlannerListFilters();
 *
 * <PlannerListFilterPills
 *   selectedCategory={category}
 *   onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
 * />
 */
export function PlannerListFilterPills({
  selectedCategory,
  onCategoryChange,
}: PlannerListFilterPillsProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="flex gap-2 flex-wrap">
      {/* All pill */}
      <button
        onClick={() => { onCategoryChange(undefined) }}
        className="selectable px-3 py-1.5 text-sm font-medium rounded-full bg-card"
        data-selected={selectedCategory === undefined}
      >
        {t('pages.plannerList.filter.all')}
      </button>

      {/* Category pills */}
      {MD_CATEGORIES.map((category) => (
        <button
          key={category}
          onClick={() => { onCategoryChange(category) }}
          className="selectable px-3 py-1.5 text-sm font-medium rounded-full bg-card"
          data-selected={selectedCategory === category}
        >
          {t(`pages.plannerList.mdCategory.${category}`)}
        </button>
      ))}
    </div>
  )
}
