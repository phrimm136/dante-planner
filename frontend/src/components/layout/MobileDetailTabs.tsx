import { useTranslation } from 'react-i18next'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'

interface MobileDetailTabsProps {
  skillsContent: React.ReactNode
  passivesContent: React.ReactNode
  thirdTabContent?: React.ReactNode
  defaultTab?: 'skills' | 'passives' | 'sanity'
}

export function MobileDetailTabs({
  skillsContent,
  passivesContent,
  thirdTabContent,
  defaultTab = 'skills',
}: MobileDetailTabsProps) {
  const { t } = useTranslation('database')
  const hasThirdTab = Boolean(thirdTabContent)

  return (
    <Tabs defaultValue={defaultTab} className="w-full">
      <TabsList className={cn('w-full grid', hasThirdTab ? 'grid-cols-3' : 'grid-cols-2')}>
        <TabsTrigger value="skills">{t('tabs.skills')}</TabsTrigger>
        <TabsTrigger value="passives">{t('passive.battle')}</TabsTrigger>
        {hasThirdTab && <TabsTrigger value="sanity">{t('sanity.title')}</TabsTrigger>}
      </TabsList>

      <TabsContent value="skills" className="mt-4">
        <div className="space-y-6">{skillsContent}</div>
      </TabsContent>

      <TabsContent value="passives" className="mt-4">
        <div className="space-y-6">{passivesContent}</div>
      </TabsContent>

      {hasThirdTab && (
        <TabsContent value="sanity" className="mt-4">
          <div className="space-y-6">{thirdTabContent}</div>
        </TabsContent>
      )}
    </Tabs>
  )
}
