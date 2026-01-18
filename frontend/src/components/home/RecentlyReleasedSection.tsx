/**
 * RecentlyReleasedSection - Home page left column
 *
 * Displays mixed Identity/EGO cards grouped by release date.
 * Simple profile images with rank/type and sinner indicators.
 *
 * Pattern: IdentityList.tsx (grid rendering)
 */

import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight } from 'lucide-react'

import { Skeleton } from '@/components/ui/skeleton'
import { useIdentityListI18n } from '@/hooks/useIdentityListData'
import { useEGOListI18n } from '@/hooks/useEGOListData'
import {
  getIdentityProfileImagePath,
  getIdentityImageFallbackPath,
  getEGOProfileImagePath,
  getSinnerIconPath,
  getRarityIconPath,
  getEGORankIconPath,
} from '@/lib/assetPaths'
import { cn, getSinnerFromId } from '@/lib/utils'
import { getSeasonColor } from '@/lib/colorUtils'

import type { DateGroup, RecentEntity } from '@/hooks/useHomePageData'

// ============================================================================
// Helpers
// ============================================================================

/**
 * Format season number to display string
 * - 0: empty (standard)
 * - 1-99: S[number]
 * - 91XX: W[number] (Walpurgis)
 * - 8000: C (Collab)
 */
function formatSeason(season: number): string {
  if (season === 0) return ''
  if (season === 8000) return 'C'
  if (season >= 9100 && season <= 9199) {
    return `W${season - 9100}`
  }
  return `S${season}`
}

// ============================================================================
// Mini Card Components
// ============================================================================

interface HomeIdentityCardProps {
  id: string
  rank: number
  season: number
}

/** Plain text identity name with suspense */
function IdentityNameText({ id }: { id: string }) {
  const names = useIdentityListI18n()
  return <>{names[id] ?? id}</>
}

/**
 * Simple identity card: profile image, icons below, then name
 */
function HomeIdentityCard({ id, rank, season }: HomeIdentityCardProps) {
  const sinner = getSinnerFromId(id)
  const seasonLabel = formatSeason(season)
  const seasonColor = getSeasonColor(season)

  return (
    <div className="flex flex-col items-center gap-1 w-28">
      {/* Profile image */}
      <div className="w-28 h-28 rounded-lg overflow-hidden bg-muted">
        <img
          src={getIdentityProfileImagePath(id, 4)}
          onError={(e) => {
            const target = e.currentTarget
            if (!target.dataset.fallback) {
              target.dataset.fallback = 'true'
              target.src = getIdentityImageFallbackPath(id)
            }
          }}
          alt=""
          loading="lazy"
          className="w-full h-full object-cover"
        />
      </div>
      {/* Icons row */}
      <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-background/80">
        <img src={getRarityIconPath(rank)} alt={String(rank)} className="h-4" />
        <img src={getSinnerIconPath(sinner)} alt={sinner} className="w-5 h-5" />
        {seasonLabel && (
          <span
            className="text-[10px] px-1 rounded text-black font-medium"
            style={{ backgroundColor: seasonColor }}
          >
            {seasonLabel}
          </span>
        )}
      </div>
      {/* Name */}
      <div className="text-xs text-center text-muted-foreground">
        <Suspense fallback={<Skeleton className="w-16 h-3" />}>
          <IdentityNameText id={id} />
        </Suspense>
      </div>
    </div>
  )
}

interface HomeEGOCardProps {
  id: string
  egoType: string
  season: number
}

/** Plain text EGO name with suspense */
function EGONameText({ id }: { id: string }) {
  const names = useEGOListI18n()
  return <>{names[id] ?? id}</>
}

/**
 * Simple EGO card: profile image, icons below, then name
 */
function HomeEGOCard({ id, egoType, season }: HomeEGOCardProps) {
  const sinner = getSinnerFromId(id)
  const seasonLabel = formatSeason(season)
  const seasonColor = getSeasonColor(season)

  return (
    <div className="flex flex-col items-center gap-1 w-28">
      {/* Profile image */}
      <div className="w-28 h-28 rounded-lg overflow-hidden bg-muted">
        <img
          src={getEGOProfileImagePath(id)}
          alt=""
          loading="lazy"
          className="w-full h-full object-cover"
        />
      </div>
      {/* Icons row */}
      <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-background/80">
        <img src={getEGORankIconPath(egoType)} alt={egoType} className="h-4" />
        <img src={getSinnerIconPath(sinner)} alt={sinner} className="w-5 h-5" />
        {seasonLabel && (
          <span
            className="text-[10px] px-1 rounded text-black font-medium"
            style={{ backgroundColor: seasonColor }}
          >
            {seasonLabel}
          </span>
        )}
      </div>
      {/* Name */}
      <div className="text-xs text-center text-muted-foreground">
        <Suspense fallback={<Skeleton className="w-16 h-3" />}>
          <EGONameText id={id} />
        </Suspense>
      </div>
    </div>
  )
}

// ============================================================================
// Entity Card Link
// ============================================================================

interface EntityCardLinkProps {
  entity: RecentEntity
}

/**
 * Link wrapper that renders the appropriate card based on entity type.
 */
function EntityCardLink({ entity }: EntityCardLinkProps) {
  if (entity.type === 'identity') {
    return (
      <Link
        to="/identity/$id"
        params={{ id: entity.data.id }}
        className="block transition-all selectable"
      >
        <HomeIdentityCard id={entity.data.id} rank={entity.data.rank} season={entity.data.season} />
      </Link>
    )
  }

  return (
    <Link
      to="/ego/$id"
      params={{ id: entity.data.id }}
      className="block transition-all selectable"
    >
      <HomeEGOCard id={entity.data.id} egoType={entity.data.egoType} season={entity.data.season} />
    </Link>
  )
}

// ============================================================================
// Main Section Component
// ============================================================================

interface RecentlyReleasedSectionProps {
  dateGroups: DateGroup[]
}

/**
 * Recently Released section for home page.
 * Shows Identity and EGO cards grouped by release date.
 */
export function RecentlyReleasedSection({ dateGroups }: RecentlyReleasedSectionProps) {
  const { t } = useTranslation('common')

  return (
    <section className="flex flex-col gap-4">
      {/* Header with browse links */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">
          {t('pages.home.recentlyReleased.title')}
        </h2>
        <div className="flex items-center gap-4 text-sm">
          <Link
            to="/identity"
            className={cn(
              'flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors'
            )}
          >
            {t('pages.home.recentlyReleased.browseIdentity')}
            <ArrowRight className="size-4" />
          </Link>
          <Link
            to="/ego"
            className={cn(
              'flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors'
            )}
          >
            {t('pages.home.recentlyReleased.browseEGO')}
            <ArrowRight className="size-4" />
          </Link>
        </div>
      </div>

      {/* Content grouped by date */}
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="flex flex-col gap-4">
          {dateGroups.map((group) => (
            <div key={group.date}>
              {/* Date header */}
              <div className="text-sm text-muted-foreground mb-3">
                {group.formattedDate}
              </div>
              {/* Card grid - left aligned */}
              <div className="flex flex-wrap gap-4">
                {group.entities.map((entity) => (
                  <EntityCardLink key={`${entity.type}-${entity.data.id}`} entity={entity} />
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
