import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'

interface MobileDetailTabsProps {
  /** Content for Skills tab */
  skillsContent: React.ReactNode
  /** Content for Passives tab */
  passivesContent: React.ReactNode
  /** Optional: Content for Sanity tab (Identity only) */
  sanityContent?: React.ReactNode
  /** Default active tab */
  defaultTab?: 'skills' | 'passives' | 'sanity'
}

/**
 * MobileDetailTabs - Tab navigation for mobile detail page layout
 *
 * On mobile, Info content is shown at top (outside tabs).
 * This component only handles the tabbed content below:
 * - Skills: Skill cards with slot selector
 * - Passives: Battle and support passives
 * - Sanity: (Identity only) Mental conditions
 *
 * Pattern: Uses shadcn/ui Tabs component
 */
export function MobileDetailTabs({
  skillsContent,
  passivesContent,
  sanityContent,
  defaultTab = 'skills',
}: MobileDetailTabsProps) {
  const hasSanity = Boolean(sanityContent)

  return (
    <Tabs defaultValue={defaultTab} className="w-full">
      <TabsList className={cn('w-full grid', hasSanity ? 'grid-cols-3' : 'grid-cols-2')}>
        <TabsTrigger value="skills">Skills</TabsTrigger>
        <TabsTrigger value="passives">Passives</TabsTrigger>
        {hasSanity && <TabsTrigger value="sanity">Sanity</TabsTrigger>}
      </TabsList>

      <TabsContent value="skills" className="mt-4">
        <div className="space-y-6">{skillsContent}</div>
      </TabsContent>

      <TabsContent value="passives" className="mt-4">
        <div className="space-y-6">{passivesContent}</div>
      </TabsContent>

      {hasSanity && (
        <TabsContent value="sanity" className="mt-4">
          <div className="space-y-6">{sanityContent}</div>
        </TabsContent>
      )}
    </Tabs>
  )
}

export default MobileDetailTabs
