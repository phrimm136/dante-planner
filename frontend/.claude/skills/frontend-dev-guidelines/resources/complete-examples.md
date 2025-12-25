# Complete Examples

Full working examples combining all patterns: data fetching, components, routing, styling, and error handling.

---

## Example 1: List Page

Complete list page with filters, search, and data fetching.

```typescript
// routes/IdentityPage.tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useEntityListData } from '@/hooks/useEntityListData'
import type { Identity } from '@/types/IdentityTypes'
import { SinnerFilter } from '@/components/common/SinnerFilter'
import { KeywordFilter } from '@/components/common/KeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
import { IdentityList } from '@/components/identity/IdentityList'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

export default function IdentityPage() {
  const { t } = useTranslation()
  const { data: identities, isPending, isError } = useEntityListData<Identity>('identity')
  const [selectedSinners, setSelectedSinners] = useState<Set<string>>(new Set())
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState<string>('')

  if (isPending) {
    return <LoadingState message={t('common.loading')} />
  }

  if (isError || !identities) {
    return (
      <ErrorState
        title={t('errors.loadFailed')}
        message={t('errors.tryAgain')}
      />
    )
  }

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.identity.title')}</h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.identity.description')}
      </p>

      <div className="bg-background rounded-lg p-6 space-y-4">
        {/* Filters and search */}
        <div className="flex gap-4 justify-between">
          <div className="flex gap-4">
            <SinnerFilter
              selectedSinners={selectedSinners}
              onSelectionChange={setSelectedSinners}
            />
            <KeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
          </div>

          <div className="shrink-0">
            <SearchBar
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              placeholder={t('pages.identity.searchBar')}
            />
          </div>
        </div>

        {/* List */}
        <IdentityList
          identities={identities}
          selectedSinners={selectedSinners}
          selectedKeywords={selectedKeywords}
          searchQuery={searchQuery}
        />
      </div>
    </div>
  )
}
```

---

## Example 2: Detail Page

Detail page with parameter validation and error handling.

```typescript
// routes/IdentityDetailPage.tsx
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { useEntityDetailData } from '@/hooks/useEntityDetailData'
import type { IdentityData, IdentityI18n } from '@/types/IdentityTypes'

export default function IdentityDetailPage() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation()

  // Validate id exists
  if (!id) {
    return (
      <ErrorState
        title={t('errors.invalidUrl')}
        message={t('errors.noIdProvided')}
      />
    )
  }

  const { data, i18n, isPending, isError } = useEntityDetailData('identity', id)
  const identityData = data as IdentityData | undefined
  const identityI18n = i18n as IdentityI18n | undefined

  if (isPending) {
    return <LoadingState />
  }

  if (isError || !identityData || !identityI18n) {
    return (
      <ErrorState
        title={t('errors.notFound')}
        message={t('errors.loadFailed')}
      />
    )
  }

  return (
    <DetailPageLayout>
      <h1 className="text-3xl font-bold mb-4">{identityI18n.name}</h1>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-muted rounded-lg p-4">
          <p className="text-sm text-muted-foreground">HP</p>
          <p className="text-2xl font-bold">{identityData.HP}</p>
        </div>
        <div className="bg-muted rounded-lg p-4">
          <p className="text-sm text-muted-foreground">{t('identity.speed')}</p>
          <p className="text-2xl font-bold">
            {identityData.minSpeed}-{identityData.maxSpeed}
          </p>
        </div>
        <div className="bg-muted rounded-lg p-4">
          <p className="text-sm text-muted-foreground">{t('identity.defense')}</p>
          <p className="text-2xl font-bold">{identityData.defLV}</p>
        </div>
      </div>

      {/* More content... */}
    </DetailPageLayout>
  )
}
```

---

## Example 3: Card Component

Reusable card with hover effects.

```typescript
// components/identity/IdentityCard.tsx
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { getIdentityImagePath } from '@/lib/assetPaths'
import type { Identity } from '@/types/IdentityTypes'

interface IdentityCardProps {
  identity: Identity
  isSelected?: boolean
}

export function IdentityCard({ identity, isSelected = false }: IdentityCardProps) {
  return (
    <Card className={cn(
      'cursor-pointer transition-all hover:shadow-md hover:scale-[1.02]',
      isSelected && 'ring-2 ring-primary'
    )}>
      <CardContent className="p-2">
        <img
          src={getIdentityImagePath(identity.id)}
          alt={identity.name}
          className="w-full aspect-square object-cover rounded"
          loading="lazy"
        />
        <p className="text-sm font-medium text-center mt-2 truncate">
          {identity.name}
        </p>
      </CardContent>
    </Card>
  )
}
```

---

## Example 4: Filter Component

Filter component with Set-based selection.

```typescript
// components/common/SinnerFilter.tsx
import { SINNERS } from '@/lib/constants'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface SinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export function SinnerFilter({
  selectedSinners,
  onSelectionChange,
}: SinnerFilterProps) {
  const toggleSinner = (sinner: string) => {
    const newSelection = new Set(selectedSinners)
    if (newSelection.has(sinner)) {
      newSelection.delete(sinner)
    } else {
      newSelection.add(sinner)
    }
    onSelectionChange(newSelection)
  }

  return (
    <div className="flex flex-wrap gap-2">
      {SINNERS.map(sinner => (
        <Button
          key={sinner}
          variant={selectedSinners.has(sinner) ? 'default' : 'outline'}
          size="sm"
          onClick={() => toggleSinner(sinner)}
          className={cn(
            'transition-all',
            selectedSinners.has(sinner) && 'ring-2 ring-primary'
          )}
        >
          {sinner}
        </Button>
      ))}
    </div>
  )
}
```

---

## Example 5: Data Hook

Hook with Zod validation and i18n support.

```typescript
// hooks/useEntityDetailData.ts
import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { validateData, getDetailSchema, getI18nSchema } from '@/lib/validation'
import type { EntityType } from '@/types/common'

const ENTITY_CONFIG = {
  identity: { dataPath: 'identity', i18nPath: 'identity', staleTime: 5 * 60 * 1000 },
  ego: { dataPath: 'ego', i18nPath: 'ego', staleTime: 5 * 60 * 1000 },
  egoGift: { dataPath: 'egoGift', i18nPath: 'egoGift', staleTime: 5 * 60 * 1000 },
} as const

export function useEntityDetailData(type: EntityType, id: string | undefined) {
  const { i18n } = useTranslation()
  const config = ENTITY_CONFIG[type]

  const dataQuery = useQuery(
    queryOptions({
      queryKey: [type, id],
      queryFn: async () => {
        const response = await fetch(`/data/${config.dataPath}/${id}.json`)
        const data = await response.json()
        return validateData(data, getDetailSchema(type), {
          entityType: type,
          dataKind: 'detail',
          id: id!,
        })
      },
      enabled: !!id,
      staleTime: config.staleTime,
    })
  )

  const i18nQuery = useQuery(
    queryOptions({
      queryKey: [type, id, 'i18n', i18n.language],
      queryFn: async () => {
        const response = await fetch(`/i18n/${i18n.language}/${config.i18nPath}/${id}.json`)
        const data = await response.json()
        return validateData(data, getI18nSchema(type), {
          entityType: type,
          dataKind: 'i18n',
          id: id!,
        })
      },
      enabled: dataQuery.isSuccess,
      staleTime: 7 * 24 * 60 * 60 * 1000,
    })
  )

  return {
    data: dataQuery.data,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
```

---

## Example 6: List Component with Filtering

List component that filters data based on props.

```typescript
// components/identity/IdentityList.tsx
import { useMemo } from 'react'
import { Link } from '@tanstack/react-router'
import { IdentityCard } from './IdentityCard'
import type { Identity } from '@/types/IdentityTypes'

interface IdentityListProps {
  identities: Identity[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  searchQuery: string
}

export function IdentityList({
  identities,
  selectedSinners,
  selectedKeywords,
  searchQuery,
}: IdentityListProps) {
  const filteredIdentities = useMemo(() => {
    return identities.filter(identity => {
      // Filter by sinner
      if (selectedSinners.size > 0) {
        // Extract sinner from identity (implementation depends on data structure)
        // if (!selectedSinners.has(identity.sinner)) return false
      }

      // Filter by keywords
      if (selectedKeywords.size > 0) {
        const hasKeyword = identity.skillKeywordList?.some(k =>
          selectedKeywords.has(k)
        )
        if (!hasKeyword) return false
      }

      // Filter by search
      if (searchQuery) {
        const searchLower = searchQuery.toLowerCase()
        if (!identity.name.toLowerCase().includes(searchLower)) {
          return false
        }
      }

      return true
    })
  }, [identities, selectedSinners, selectedKeywords, searchQuery])

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
      {filteredIdentities.map(identity => (
        <Link
          key={identity.id}
          to="/identity/$id"
          params={{ id: identity.id }}
        >
          <IdentityCard identity={identity} />
        </Link>
      ))}
    </div>
  )
}
```

---

## Summary

**Key patterns demonstrated:**

1. **List Page**: Filters + search + data fetching + error handling
2. **Detail Page**: Parameter validation + loading/error states
3. **Card Component**: Reusable, styled with cn()
4. **Filter Component**: Set-based selection pattern
5. **Data Hook**: Zod validation + i18n + caching
6. **List Component**: Memoized filtering

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [data-fetching.md](data-fetching.md) - Query patterns
- [schemas-and-validation.md](schemas-and-validation.md) - Zod validation
