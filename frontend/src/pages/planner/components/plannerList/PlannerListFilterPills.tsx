import { useTranslation } from 'react-i18next'

import { MD_CATEGORIES } from '@/shared/gameData'

import { MdCategoryLabel } from '../MdCategoryLabel'

import type { MDCategory } from '@/shared/gameData'

interface PlannerListFilterPillsProps {
  selectedCategory: MDCategory | undefined
  onCategoryChange: (category: MDCategory | undefined) => void
}

export function PlannerListFilterPills({
  selectedCategory,
  onCategoryChange,
}: PlannerListFilterPillsProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="flex gap-2 flex-wrap">
      <button
        onClick={() => {
          onCategoryChange(undefined)
        }}
        className="selectable px-3 py-1.5 text-sm font-medium rounded-full bg-card"
        data-selected={selectedCategory === undefined}
      >
        {t('pages.plannerList.filter.all')}
      </button>

      {MD_CATEGORIES.map((category) => (
        <button
          key={category}
          onClick={() => {
            onCategoryChange(category)
          }}
          className="selectable px-3 py-1.5 text-sm font-medium rounded-full bg-card"
          data-selected={selectedCategory === category}
        >
          <MdCategoryLabel category={category} />
        </button>
      ))}
    </div>
  )
}
