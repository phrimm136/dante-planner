/**
 * BannerSection - Home page hero banner
 *
 * Displays the promotional banner with a text overlay and CTA.
 */

import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { getBannerImagePath } from '@/shared/assets'

const BANNER_LINK = '/planner/md/new'

export function BannerSection() {
  const { t } = useTranslation('common')
  const title = t('pages.home.banner.md.title')

  return (
    <div
      className="relative w-full overflow-hidden rounded-lg"
      aria-label={t('a11y.bannerCarousel')}
    >
      <div className="relative aspect-[16/9] w-full" aria-live="polite" aria-atomic="true">
        <div className="absolute inset-0 transition-opacity duration-500 opacity-100">
          {/* Background */}
          <img
            src={getBannerImagePath()}
            alt={title}
            className="size-full object-cover"
            fetchPriority="high"
            loading="eager"
          />

          {/* Gradient overlay for text readability */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />

          {/* Text content */}
          <div className="absolute inset-0 flex flex-col justify-end p-6 lg:p-10">
            <div className="max-w-2xl">
              <h2 className="text-2xl font-bold text-white lg:text-4xl mb-2">{title}</h2>
              <p className="text-sm text-white/80 lg:text-base mb-2">
                {t('pages.home.banner.md.subtitle')}
              </p>
            </div>

            {/* CTA Button - own row, aligned right */}
            <div className="flex justify-end mt-2">
              <Button asChild size="lg" className="bg-primary hover:bg-primary/90">
                <Link to={BANNER_LINK}>
                  {t('pages.home.banner.md.cta')}
                  <ArrowRight className="ml-2 size-4" />
                </Link>
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
