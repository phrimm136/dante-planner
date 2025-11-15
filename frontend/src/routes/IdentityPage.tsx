import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IdentitySinnerFilter } from '@/components/identity/IdentitySinnerFilter'
import { IdentityKeywordFilter } from '@/components/identity/IdentityKeywordFilter'
import { IdentitySearchBar } from '@/components/identity/IdentitySearchBar'
import { IdentityList } from '@/components/identity/IdentityList'

export default function IdentityPage() {
  const { t } = useTranslation()
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.identity.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.identity.description')}
      </p>

      {/* Main content area matching mockup */}
      <div className="bg-background rounded-lg p-6 space-y-4">
        {/* Top row: Filters on left, Search bar on right with space between */}
        <div className="flex gap-4 justify-between">
          {/* Left side: Filters */}
          <div className="flex gap-4">
            <IdentitySinnerFilter
              selectedSinners={selectedSinners}
              onSelectionChange={setSelectedSinners}
            />
            <IdentityKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
          </div>

          {/* Right side: Search bar */}
          <div className="shrink-0">
            <IdentitySearchBar />
          </div>
        </div>

        {/* Bottom: Identity list */}
        <div>
          <IdentityList selectedSinners={selectedSinners} selectedKeywords={selectedKeywords} />
        </div>
      </div>
    </div>
  )
}
