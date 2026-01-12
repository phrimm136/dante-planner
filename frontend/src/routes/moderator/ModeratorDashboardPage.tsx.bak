import { useTranslation } from 'react-i18next'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { RecommendedPlannerListWithSuspense } from '@/components/moderator/RecommendedPlannerList'
import { HiddenPlannerList } from '@/components/moderator/HiddenPlannerList'

/**
 * ModeratorDashboardPage - Moderation dashboard (ROLE_MODERATOR or ROLE_ADMIN)
 *
 * Provides two tabs:
 * 1. Recommended Review - Current recommended planners with hide controls
 * 2. Hidden Planners - Planners hidden from recommended with unhide controls
 *
 * Route protection: Frontend UI guard only - backend has @PreAuthorize
 */
export function ModeratorDashboardPage() {
  const { t } = useTranslation('planner')

  return (
    <div className="container mx-auto py-8 px-4">
      <h1 className="text-3xl font-bold mb-6">{t('moderation.title')}</h1>

      <Tabs defaultValue="recommended" className="w-full">
        <TabsList className="grid w-full grid-cols-2 lg:w-[400px]">
          <TabsTrigger value="recommended">
            {t('moderation.recommendedTab')}
          </TabsTrigger>
          <TabsTrigger value="hidden">{t('moderation.hiddenTab')}</TabsTrigger>
        </TabsList>

        <TabsContent value="recommended" className="mt-6">
          <RecommendedPlannerListWithSuspense />
        </TabsContent>

        <TabsContent value="hidden" className="mt-6">
          <HiddenPlannerList />
        </TabsContent>
      </Tabs>
    </div>
  )
}
