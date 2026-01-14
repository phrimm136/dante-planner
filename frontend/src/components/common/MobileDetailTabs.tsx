import { useTranslation } from 'react-i18next'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'

interface MobileDetailTabsProps {
  /** Content for Skills tab */
  skillsContent: React.ReactNode
  /** Content for Passives tab */
  passivesContent: React.ReactNode
  /** Optional: Content for third tab (Sanity for Identity only) */
  thirdTabContent?: React.ReactNode
  /** Default active tab */
  defaultTab?: 'skills' | 'passives' | 'sanity'
}

/**
 * MobileDetailTabs - Tab navigation for mobile detail page layout
 *
 * On mobile, Info content is shown at top (outside tabs).
 * This component only handles the tabbed content below:
 * - Skills: Skill cards with slot/type selector
 * - Passives: Effective and locked passives
 * - Third tab (optional): Sanity (Identity only)
 *
 * Pattern: Uses shadcn/ui Tabs component
 */
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

export default MobileDetailTabs
