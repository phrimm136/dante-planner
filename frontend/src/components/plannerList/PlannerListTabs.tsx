import { useTranslation } from 'react-i18next'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

import type { PlannerListView } from '@/types/PlannerListTypes'

interface PlannerListTabsProps {
  /** Current view mode */
  value: PlannerListView
  /** Callback when view changes */
  onChange: (view: PlannerListView) => void
}

/**
 * Tab switcher for My Plans / Community views.
 * Uses shadcn Tabs but only renders triggers (no content).
 * Content is rendered separately based on selected view.
 *
 * @example
 * const { view, setFilters } = usePlannerListFilters();
 *
 * <PlannerListTabs
 *   value={view}
 *   onChange={(v) => setFilters({ view: v, page: 0 })}
 * />
 */
export function PlannerListTabs({ value, onChange }: PlannerListTabsProps) {
  const { t } = useTranslation()

  return (
    <Tabs
      value={value}
      onValueChange={(v) => { onChange(v as PlannerListView) }}
    >
      <TabsList>
        <TabsTrigger value="my-plans">
          {t('pages.plannerList.tabs.myPlans')}
        </TabsTrigger>
        <TabsTrigger value="community">
          {t('pages.plannerList.tabs.community')}
        </TabsTrigger>
      </TabsList>
    </Tabs>
  )
}
