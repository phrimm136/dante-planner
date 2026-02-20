/**
 * HomePage - Landing page for Limbus Planner
 *
 * Route: /
 *
 * Layout:
 * - Banner carousel at top (MD planner + Extraction calculator)
 * - Two columns: Recently Released (left) + Community Plans (right)
 * - Responsive: side-by-side on desktop (≥1024px), stacked on mobile
 *
 * Pattern: PlannerMDGesellschaftPage.tsx (Suspense wrapping)
 */

import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { LoadingState } from '@/components/common/LoadingState'
import { BannerSection } from '@/components/home/BannerSection'
import { AnnouncementContent } from '@/components/home/AnnouncementContent'
import { AnnouncementSkeleton } from '@/components/home/AnnouncementSection'
import { RecentlyReleasedSection } from '@/components/home/RecentlyReleasedSection'
import { CommunityPlansSection } from '@/components/home/CommunityPlansSection'

import { useRecentlyReleasedData } from '@/hooks/useHomePageData'

// ============================================================================
// Inner Content Component
// ============================================================================

/**
 * Recently Released content that uses Suspense-aware hook.
 * Must be wrapped in Suspense boundary.
 */
function RecentlyReleasedContent() {
  const { i18n } = useTranslation()
  const { dateGroups } = useRecentlyReleasedData(i18n.language)

  return <RecentlyReleasedSection dateGroups={dateGroups} />
}

// ============================================================================
// Page Content Component
// ============================================================================

function HomePageContent() {
  return (
    <div className="container mx-auto p-8">
      {/* Banner carousel */}
      <div className="mb-8">
        <BannerSection />
      </div>

      {/* Announcement section */}
      <div className="mb-8">
        <Suspense fallback={<AnnouncementSkeleton />}>
          <AnnouncementContent />
        </Suspense>
      </div>

      {/* Two-column layout: Recently Released + Community Plans */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Left column: Recently Released */}
        <Suspense fallback={<LoadingState />}>
          <RecentlyReleasedContent />
        </Suspense>

        {/* Right column: Community Plans */}
        <CommunityPlansSection />
      </div>
    </div>
  )
}

// ============================================================================
// Main Page Component
// ============================================================================

/**
 * HomePage - Landing page with ErrorBoundary and Suspense
 */
export default function HomePage() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<LoadingState />}>
        <HomePageContent />
      </Suspense>
    </ErrorBoundary>
  )
}
