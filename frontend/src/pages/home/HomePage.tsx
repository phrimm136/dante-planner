import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { ErrorBoundary } from '@/components/feedback/ErrorBoundary'
import { LoadingState } from '@/components/feedback/LoadingState'
import { BannerSection } from './components/BannerSection'
import { AnnouncementContent } from './components/AnnouncementContent'
import { AnnouncementSkeleton } from './components/AnnouncementSection'
import { SideLinkSection } from './components/SideLinkSection'
import {
  RecentlyReleasedSection,
  RecentlyReleasedSkeleton,
} from './components/RecentlyReleasedSection'
import { CommunityPlansSection } from './components/CommunityPlansSection'

import { useRecentlyReleasedData } from './hooks/useHomePageData'
import { SECTION_STYLES } from '@/lib/constants'

function RecentlyReleasedContent() {
  const { i18n } = useTranslation()
  const { dateGroups } = useRecentlyReleasedData(i18n.language)

  return <RecentlyReleasedSection dateGroups={dateGroups} />
}

function HomePageContent() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="mb-8">
        <BannerSection />
      </div>

      <div className="mb-8 grid grid-cols-1 lg:grid-cols-[7fr_1fr] gap-4">
        <Suspense fallback={<AnnouncementSkeleton />}>
          <AnnouncementContent />
        </Suspense>
        <SideLinkSection />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <Suspense fallback={<RecentlyReleasedSkeleton />}>
          <RecentlyReleasedContent />
        </Suspense>

        <CommunityPlansSection />
      </div>
    </div>
  )
}

export default function HomePage() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<LoadingState />}>
        <HomePageContent />
      </Suspense>
    </ErrorBoundary>
  )
}
