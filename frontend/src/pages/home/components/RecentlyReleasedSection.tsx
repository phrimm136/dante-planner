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
import type { SinnerScopedId } from '@/shared/gameData'
import { cn } from '@/lib/utils'
import { getSeasonColor } from '@/shared/gameData'

import type { ReactNode } from 'react'
import type { DateGroup, RecentEntity } from '../hooks/useHomePageData'
import { SECTION_STYLES } from '@/lib/constants'
import { CardSlot } from '@/shared/cardLayout'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { RECENT_RELEASE_GEOMETRY } from '../lib/cardLayout'

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

function createEntityNameText(useNames: () => Record<string, string>) {
  return function EntityNameText({ id }: { id: SinnerScopedId }) {
    const names = useNames()
    return <>{names[id] ?? id}</>
  }
}

const IdentityNameText = createEntityNameText(useIdentityListI18n)
const EGONameText = createEntityNameText(useEGOListI18n)

interface HomeEntityCardProps {
  id: SinnerScopedId
  season: number
  imageSrc: string
  imageFallbackSrc: string
  gradeIconSrc: string
  gradeIconAlt: string
  nameText: ReactNode
}

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
    <div className="flex flex-col items-center gap-1">
      <CardSlot
        size={RECENT_RELEASE_GEOMETRY.size}
        mobileScale={RECENT_RELEASE_GEOMETRY.mobileScale}
        className="rounded-lg overflow-hidden bg-muted"
      >
        <FallbackImage
          src={imageSrc}
          fallbackSrc={imageFallbackSrc}
          alt=""
          loading="lazy"
          className="w-full h-full object-cover"
        />
      </CardSlot>
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
      <div className="text-xs text-center text-muted-foreground">
        <Suspense fallback={<Skeleton className="w-16 h-3" />}>{nameText}</Suspense>
      </div>
    </div>
  )
}

const ENTITY_ROUTES: Record<RecentEntity['type'], '/identity/$id' | '/ego/$id'> = {
  identity: '/identity/$id',
  ego: '/ego/$id',
}

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

interface RecentlyReleasedSectionProps {
  dateGroups: DateGroup[]
}

export function RecentlyReleasedSkeleton() {
  return (
    <section className={SECTION_STYLES.LAYOUT.column}>
      <div className={SECTION_STYLES.LAYOUT.rowBetween}>
        <Skeleton className="h-7 w-40" />
        <div className="flex items-center gap-4">
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-16" />
        </div>
      </div>

      <div className={cn(SECTION_STYLES.panel, 'flex-1')}>
        <div className={SECTION_STYLES.LAYOUT.column}>
          {Array.from({ length: 3 }).map((_, groupIdx) => (
            <div key={groupIdx}>
              <Skeleton className="mb-3 h-4 w-24" />
              <div
                className="grid gap-2"
                style={{ gridTemplateColumns: 'repeat(auto-fit, 112px)', justifyContent: 'start' }}
              >
                {Array.from({ length: 7 }).map((_, cardIdx) => (
                  <div key={cardIdx} className="flex flex-col items-center gap-1">
                    <CardSlot
                      size={RECENT_RELEASE_GEOMETRY.size}
                      mobileScale={RECENT_RELEASE_GEOMETRY.mobileScale}
                    >
                      <Skeleton className="size-full rounded-lg" />
                    </CardSlot>
                    <TextSkeleton width="xs" />
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

      <div className={cn(SECTION_STYLES.panel, 'flex-1')}>
        <div className={SECTION_STYLES.LAYOUT.column}>
          {dateGroups.map((group) => (
            <div key={group.date}>
              <div className="text-sm text-muted-foreground mb-3">{group.formattedDate}</div>
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
