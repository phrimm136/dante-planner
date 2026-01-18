/**
 * BannerSection - Home page hero banner carousel
 *
 * Displays rotating promotional banners with text overlays and CTAs.
 * Supports both image-based and gradient-based backgrounds.
 * Auto-advances with manual navigation support.
 *
 * Pattern: RecentlyReleasedSection.tsx (same directory)
 */

import { useState, useEffect } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { getBannerImagePath } from '@/lib/assetPaths'
import { BANNER_CAROUSEL_INTERVAL } from '@/lib/constants'
import { useFilterI18nData } from '@/hooks/useFilterI18nData'

// ============================================================================
// Constants
// ============================================================================

/**
 * Current Walpurgis Night number for extraction calculator banner
 * Update this when new Walpurgis events are released
 */
// const CURRENT_WALPURGIS_NUMBER = 7

/**
 * Season key for Walpurgis in seasons.json
 * Format: 9100 + walpurgis number (e.g., 9107 for 7th)
 */
// const WALPURGIS_SEASON_KEY = `${9100 + CURRENT_WALPURGIS_NUMBER}`

// ============================================================================
// Types
// ============================================================================

interface BannerSlide {
  id: string
  type: 'image' | 'gradient'
  background: string
  titleKey?: string // i18n key: pages.home.banner.{titleKey} (if i18n)
  seasonKey?: string // seasons.json key for dynamic title
  subtitleKey: string // i18n key: pages.home.banner.{subtitleKey}
  ctaKey: string // i18n key: pages.home.banner.{ctaKey}
  link: string
}

// ============================================================================
// Slide Configuration
// ============================================================================

const BANNER_SLIDES: BannerSlide[] = [
  {
    id: 'md6',
    type: 'image',
    background: getBannerImagePath(6),
    titleKey: 'md.title',
    subtitleKey: 'md.subtitle',
    ctaKey: 'md.cta',
    link: '/planner/md/new',
  },
  // Extraction calculator banner - enable when feature is ready
  // {
  //   id: 'extraction',
  //   type: 'gradient',
  //   background: 'bg-gradient-to-r from-violet-900 via-purple-800 to-indigo-900',
  //   seasonKey: WALPURGIS_SEASON_KEY,
  //   subtitleKey: 'extraction.subtitle',
  //   ctaKey: 'extraction.cta',
  //   link: '/planner/extraction',
  // },
]

// ============================================================================
// Component
// ============================================================================

export function BannerSection() {
  const { t } = useTranslation('common')
  const { seasonsI18n } = useFilterI18nData()
  const [activeIndex, setActiveIndex] = useState(0)

  // Auto-advance carousel
  useEffect(() => {
    if (BANNER_SLIDES.length <= 1) return

    const timer = setInterval(() => {
      setActiveIndex((prev) => (prev + 1) % BANNER_SLIDES.length)
    }, BANNER_CAROUSEL_INTERVAL)

    return () => clearInterval(timer)
  }, [])

  // Handle manual navigation
  const goToSlide = (index: number) => {
    setActiveIndex(index)
  }

  const goToPrev = () => {
    setActiveIndex((prev) => (prev - 1 + BANNER_SLIDES.length) % BANNER_SLIDES.length)
  }

  const goToNext = () => {
    setActiveIndex((prev) => (prev + 1) % BANNER_SLIDES.length)
  }

  // Get title for a slide
  const getSlideTitle = (slide: BannerSlide): string => {
    // Use seasons.json for dynamic titles (e.g., Walpurgis)
    if (slide.seasonKey) {
      const seasonTitle = seasonsI18n[slide.seasonKey as keyof typeof seasonsI18n]
      if (seasonTitle) return seasonTitle
    }
    // Use i18n for static titles
    if (slide.titleKey) {
      return t(`pages.home.banner.${slide.titleKey}`)
    }
    return ''
  }

  return (
    <div className="relative w-full overflow-hidden rounded-lg" aria-label={t('a11y.bannerCarousel')}>
      {/* Banner slides */}
      <div className="relative aspect-[21/9] w-full" aria-live="polite" aria-atomic="true">
        {BANNER_SLIDES.map((slide, index) => (
          <div
            key={slide.id}
            className={cn(
              'absolute inset-0 transition-opacity duration-500',
              index === activeIndex ? 'opacity-100' : 'opacity-0 pointer-events-none'
            )}
          >
            {/* Background */}
            {slide.type === 'image' ? (
              <img
                src={slide.background}
                alt={getSlideTitle(slide)}
                className="size-full object-cover"
              />
            ) : (
              <div className={cn('size-full', slide.background)} />
            )}

            {/* Gradient overlay for text readability */}
            <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />

            {/* Text content */}
            <div className="absolute inset-0 flex flex-col justify-end p-6 lg:p-10">
              <div className="max-w-2xl">
                <h2 className="text-2xl font-bold text-white lg:text-4xl mb-2">
                  {getSlideTitle(slide)}
                </h2>
                <p className="text-sm text-white/80 lg:text-base mb-4 line-clamp-2">
                  {t(`pages.home.banner.${slide.subtitleKey}`)}
                </p>
              </div>

              {/* CTA Button - bottom right */}
              <div className="absolute bottom-6 right-6 lg:bottom-10 lg:right-10">
                <Button asChild size="lg" className="bg-primary hover:bg-primary/90">
                  <Link to={slide.link}>
                    {t(`pages.home.banner.${slide.ctaKey}`)}
                    <ArrowRight className="ml-2 size-4" />
                  </Link>
                </Button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Arrow navigation */}
      {BANNER_SLIDES.length > 1 && (
        <>
          <button
            type="button"
            onClick={goToPrev}
            className="absolute left-4 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white transition-colors hover:bg-black/50"
            aria-label={t('a11y.previousSlide')}
          >
            <ChevronLeft className="size-6" />
          </button>
          <button
            type="button"
            onClick={goToNext}
            className="absolute right-4 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white transition-colors hover:bg-black/50"
            aria-label={t('a11y.nextSlide')}
          >
            <ChevronRight className="size-6" />
          </button>
        </>
      )}

      {/* Navigation dots */}
      {BANNER_SLIDES.length > 1 && (
        <div className="absolute bottom-6 left-1/2 flex -translate-x-1/2 gap-2 lg:bottom-10">
          {BANNER_SLIDES.map((slide, index) => (
            <button
              key={slide.id}
              type="button"
              onClick={() => goToSlide(index)}
              className={cn(
                'size-3 rounded-full transition-colors',
                index === activeIndex
                  ? 'bg-white'
                  : 'bg-white/50 hover:bg-white/75'
              )}
              aria-label={`Go to slide ${index + 1}`}
            />
          ))}
        </div>
      )}
    </div>
  )
}
