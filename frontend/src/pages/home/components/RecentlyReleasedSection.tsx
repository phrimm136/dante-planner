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

import { FallbackImage } from '@/components/ui/FallbackImage'
import { Skeleton } from '@/components/ui/skeleton'
import { useIdentityListI18n } from '@/pages/identity'
import { useEGOListI18n } from '@/pages/ego'
import {
  getIdentityProfileImagePath,
  getIdentityImageFallbackPath,
  getEGOProfileImagePath,
  getSinnerIconPath,
  getRarityIconPath,
  getEGORankIconPath,
} from '@/shared/assets'
import { getSinnerFromId } from '@/shared/gameData'
import { cn } from '@/lib/utils'
import { getSeasonColor } from '@/shared/gameData'

import type { ReactNode } from 'react'
import type { DateGroup, RecentEntity } from '../hooks/useHomePageData'
import { SECTION_STYLES } from '@/lib/constants'

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

/** Plain text entity name with suspense, bound to one entity kind's i18n hook. */
function createEntityNameText(useNames: () => Record<string, string>) {
  return function EntityNameText({ id }: { id: string }) {
    const names = useNames()
    return <>{names[id] ?? id}</>
  }
}

const IdentityNameText = createEntityNameText(useIdentityListI18n)
const EGONameText = createEntityNameText(useEGOListI18n)

interface HomeEntityCardProps {
  id: string
  season: number
  imageSrc: string
  /** Used when `imageSrc` fails; equal to `imageSrc` for kinds with no alternative. */
  imageFallbackSrc: string
  /** Rarity for an identity, rank for an EGO. */
  gradeIconSrc: string
  gradeIconAlt: string
  nameText: ReactNode
}

/**
 * Simple entity card: profile image, icons below, then name
 */
function HomeEntityCard({
  id,
  season,
  imageSrc,
  imageFallbackSrc,
  gradeIconSrc,
  gradeIconAlt,
  nameText,
}: HomeEntityCardProps) {
  const sinner = getSinnerFromId(id)
  const seasonLabel = formatSeason(season)
  const seasonColor = getSeasonColor(season)

  return (
    <div className="flex flex-col items-center gap-1 w-28">
      {/* Profile image */}
      <div className="w-28 h-28 rounded-lg overflow-hidden bg-muted">
        <FallbackImage
          src={imageSrc}
          fallbackSrc={imageFallbackSrc}
          alt=""
          loading="lazy"
          className="w-full h-full object-cover"
        />
      </div>
      {/* Icons row */}
      <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-background/80">
        <img src={gradeIconSrc} alt={gradeIconAlt} className="h-4" />
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
        <Suspense fallback={<Skeleton className="w-16 h-3" />}>{nameText}</Suspense>
      </div>
    </div>
  )
}

// ============================================================================
// Entity Card Link
// ============================================================================

const ENTITY_ROUTES: Record<RecentEntity['type'], '/identity/$id' | '/ego/$id'> = {
  identity: '/identity/$id',
  ego: '/ego/$id',
}

/** Per-kind assets and name source for one recently released entity. */
function entityCardProps(entity: RecentEntity): HomeEntityCardProps {
  if (entity.type === 'identity') {
    const { id, rank, season } = entity.data
    return {
      id,
      season,
      imageSrc: getIdentityProfileImagePath(id, 4),
      imageFallbackSrc: getIdentityImageFallbackPath(id),
      gradeIconSrc: getRarityIconPath(rank),
      gradeIconAlt: String(rank),
      nameText: <IdentityNameText id={id} />,
    }
  }

  const { id, egoType, season } = entity.data
  return {
    id,
    season,
    imageSrc: getEGOProfileImagePath(id),
    imageFallbackSrc: getEGOProfileImagePath(id),
    gradeIconSrc: getEGORankIconPath(egoType),
    gradeIconAlt: egoType,
    nameText: <EGONameText id={id} />,
  }
}

interface EntityCardLinkProps {
  entity: RecentEntity
}

/**
 * Link wrapper that renders the appropriate card based on entity type.
 */
function EntityCardLink({ entity }: EntityCardLinkProps) {
  return (
    <Link
      to={ENTITY_ROUTES[entity.type]}
      params={{ id: entity.data.id }}
      className="block transition-all selectable"
    >
      <HomeEntityCard {...entityCardProps(entity)} />
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
/**
 * Loading placeholder for RecentlyReleasedSection. Mirrors the real layout —
 * header row, bordered content box, and date-grouped 112px card cells — so the
 * home left column reserves its height and does not shove the footer when the
 * data resolves. Card count/rows are representative, not exact; the goal is a
 * stable row height for the two-column grid, not pixel identity.
 */
export function RecentlyReleasedSkeleton() {
  return (
    <section className={SECTION_STYLES.LAYOUT.column}>
      {/* Header: title + browse links */}
      <div className={SECTION_STYLES.LAYOUT.rowBetween}>
        <Skeleton className="h-7 w-40" />
        <div className="flex items-center gap-4">
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-16" />
        </div>
      </div>

      {/* Content box with date groups */}
      <div className={SECTION_STYLES.panel}>
        <div className={SECTION_STYLES.LAYOUT.column}>
          {Array.from({ length: 3 }).map((_, groupIdx) => (
            <div key={groupIdx}>
              <Skeleton className="mb-3 h-4 w-24" />
              <div
                className="grid gap-2"
                style={{ gridTemplateColumns: 'repeat(auto-fit, 112px)', justifyContent: 'start' }}
              >
                {Array.from({ length: 7 }).map((_, cardIdx) => (
                  <div key={cardIdx} className="flex flex-col items-center gap-1 w-28">
                    <Skeleton className="w-28 h-28 rounded-lg" />
                    <Skeleton className="h-3 w-20" />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export function RecentlyReleasedSection({ dateGroups }: RecentlyReleasedSectionProps) {
  const { t } = useTranslation('common')

  return (
    <section className={SECTION_STYLES.LAYOUT.column}>
      {/* Header with browse links */}
      <div className={SECTION_STYLES.LAYOUT.rowBetween}>
        <h2 className="text-xl font-semibold">{t('pages.home.recentlyReleased.title')}</h2>
        <div className="flex items-center gap-4 text-sm">
          <Link
            to="/identity"
            className={cn(
              'flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors',
            )}
          >
            {t('pages.home.recentlyReleased.browseIdentity')}
            <ArrowRight className="size-4" />
          </Link>
          <Link
            to="/ego"
            className={cn(
              'flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors',
            )}
          >
            {t('pages.home.recentlyReleased.browseEGO')}
            <ArrowRight className="size-4" />
          </Link>
        </div>
      </div>

      {/* Content grouped by date */}
      <div className={SECTION_STYLES.panel}>
        <div className={SECTION_STYLES.LAYOUT.column}>
          {dateGroups.map((group) => (
            <div key={group.date}>
              {/* Date header */}
              <div className="text-sm text-muted-foreground mb-3">{group.formattedDate}</div>
              {/* Card grid - auto-fit creates only columns needed */}
              <div
                className="grid gap-2"
                style={{ gridTemplateColumns: 'repeat(auto-fit, 112px)', justifyContent: 'start' }}
              >
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
