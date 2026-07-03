/**
 * Planner Filter Pane
 *
 * Collapsible panel with 5 filter categories for searching planners by content items.
 * Categories: Keywords, Identity, EGO, EGO Gift, Theme Pack
 *
 * - Row 1: Search input to narrow displayed filter items across all categories
 * - Row 2: Selected item chips (removable)
 * - Row 3: Category navigation buttons with match counts
 * - Section content: Icon-button grids per category
 *
 * Reusable across published plan list and personal plan list pages.
 * Composes with usePlannerSearchFilters hook for URL param sync.
 *
 * Pattern: Collapsible (shadcn/ui) + selectable buttons (data-selected pattern)
 *
 * Architecture: Two-component split for progressive loading.
 * - PlannerFilterPane (outer): No suspense. Renders keywords immediately.
 * - HeavySections (inner): Suspense-wrapped. Loads identity/ego/gift/themePack data.
 */

import { useState, useRef, useMemo, useCallback, useEffect, forwardRef, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown, ChevronUp, X } from 'lucide-react'

import { Collapsible, CollapsibleTrigger, CollapsibleContent } from '@/components/ui/collapsible'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'

import { useIdentityListSpec, useIdentityListI18n } from '@/pages/identity'
import { useEGOListSpec, useEGOListI18n } from '@/pages/ego'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { useThemePackListData } from '@/pages/themePack'
import { usePlannerKeywordsI18nDeferred } from '../../hooks/usePlannerKeywordsI18n'

import { PLANNER_KEYWORDS, SINNERS } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import {
  getKeywordIconPath,
  getIdentityInfoImagePath,
  getEGOImagePath,
  getEGOGiftIconPath,
  getThemePackImagePath,
  getSinnerIconPath,
} from '@/shared/assets'

import type { PlannerSearchFilters } from '../../types/PlannerSearchTypes'

// ============================================================================
// Constants
// ============================================================================

const FILTER_CATEGORIES = ['keywords', 'identity', 'ego', 'gift', 'themePack'] as const
type FilterCategory = typeof FILTER_CATEGORIES[number]

// ============================================================================
// Types
// ============================================================================

interface FilterItem {
  id: string
  label: string
  category: FilterCategory
  group?: string
}

interface PlannerFilterPaneProps {
  filters: PlannerSearchFilters
  onFiltersChange: (updates: Partial<PlannerSearchFilters>) => void
}

// ============================================================================
// Component (outer, no suspense)
// ============================================================================

/**
 * Collapsible filter pane for searching planners by content items.
 *
 * @example
 * ```tsx
 * const { filters, setFilters } = usePlannerSearchFilters()
 * <PlannerFilterPane filters={filters} onFiltersChange={setFilters} />
 * ```
 */
export function PlannerFilterPane({ filters, onFiltersChange }: PlannerFilterPaneProps) {
  const { t } = useTranslation(['planner', 'sinnerNames'])
  const [isOpen, setIsOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const sectionRefs = useRef<Record<FilterCategory, HTMLDivElement | null>>({
    keywords: null,
    identity: null,
    ego: null,
    gift: null,
    themePack: null,
  })

  // Keywords load eagerly via non-suspending hook (tiny data, always visible)
  const keywordsI18n = usePlannerKeywordsI18nDeferred()

  // Keyword items (always available)
  const keywordItems = useMemo((): FilterItem[] =>
    PLANNER_KEYWORDS.map((kw) => ({
      id: kw, label: keywordsI18n[kw]?.label ?? kw, category: 'keywords' as FilterCategory,
    })),
  [keywordsI18n])

  // Heavy items populated by HeavySections via callback
  const [heavyItems, setHeavyItems] = useState<FilterItem[]>([])
  const allItems = useMemo(() => [...keywordItems, ...heavyItems], [keywordItems, heavyItems])

  // Filter items by search query
  const lowerQuery = searchQuery.toLowerCase()
  const filteredItems = useMemo(() => {
    if (!lowerQuery) return allItems
    return allItems.filter((item) =>
      item.label.toLowerCase().includes(lowerQuery) ||
      item.id.toLowerCase().includes(lowerQuery)
    )
  }, [allItems, lowerQuery])

  // Match counts per category
  const matchCounts = useMemo(() => {
    const counts: Record<FilterCategory, number> = {
      keywords: 0,
      identity: 0,
      ego: 0,
      gift: 0,
      themePack: 0,
    }
    for (const item of filteredItems) {
      counts[item.category]++
    }
    return counts
  }, [filteredItems])

  // Get items for a specific category
  const getItemsForCategory = useCallback((category: FilterCategory) => {
    return filteredItems.filter((item) => item.category === category)
  }, [filteredItems])

  // Check if an item is selected
  const isSelected = useCallback((category: FilterCategory, id: string): boolean => {
    switch (category) {
      case 'keywords': return filters.keywords.includes(id)
      case 'identity': return filters.identityIds.includes(id)
      case 'ego': return filters.egoIds.includes(id)
      case 'gift': return filters.giftIds.includes(id)
      case 'themePack': return filters.themePackIds.includes(id)
    }
  }, [filters])

  // Toggle selection of an item
  const toggleItem = useCallback((category: FilterCategory, id: string) => {
    const toggle = (arr: string[]) =>
      arr.includes(id) ? arr.filter((v) => v !== id) : [...arr, id]

    switch (category) {
      case 'keywords':
        onFiltersChange({ keywords: toggle(filters.keywords) })
        break
      case 'identity':
        onFiltersChange({ identityIds: toggle(filters.identityIds) })
        break
      case 'ego':
        onFiltersChange({ egoIds: toggle(filters.egoIds) })
        break
      case 'gift':
        onFiltersChange({ giftIds: toggle(filters.giftIds) })
        break
      case 'themePack':
        onFiltersChange({ themePackIds: toggle(filters.themePackIds) })
        break
    }
  }, [filters, onFiltersChange])

  // Build selected chips — uses allItems for label lookup, falls back to raw ID
  const selectedChips = useMemo(() => {
    const itemMap = new Map(allItems.map((item) => [`${item.category}-${item.id}`, item]))
    const chips: FilterItem[] = []

    for (const kw of filters.keywords) {
      const found = itemMap.get(`keywords-${kw}`)
      chips.push({ id: kw, label: found?.label ?? kw, category: 'keywords' })
    }
    for (const id of filters.identityIds) {
      const found = itemMap.get(`identity-${id}`)
      chips.push({ id, label: found?.label ?? id, category: 'identity' })
    }
    for (const id of filters.egoIds) {
      const found = itemMap.get(`ego-${id}`)
      chips.push({ id, label: found?.label ?? id, category: 'ego' })
    }
    for (const id of filters.giftIds) {
      const found = itemMap.get(`gift-${id}`)
      chips.push({ id, label: found?.label ?? id, category: 'gift' })
    }
    for (const id of filters.themePackIds) {
      const found = itemMap.get(`themePack-${id}`)
      chips.push({ id, label: found?.label ?? id, category: 'themePack' })
    }
    return chips
  }, [filters, allItems])

  const scrollContainerRef = useRef<HTMLDivElement>(null)

  // Scroll to category section within the pane's scroll container
  const scrollToCategory = useCallback((category: FilterCategory) => {
    const el = sectionRefs.current[category]
    const container = scrollContainerRef.current
    if (!el || !container) return
    container.scrollTo({
      top: el.offsetTop - container.offsetTop,
      behavior: 'instant',
    })
  }, [])

  // Category display labels
  const categoryLabels: Record<FilterCategory, string> = {
    keywords: t('filterPane.keywords'),
    identity: t('filterPane.identity'),
    ego: t('filterPane.ego'),
    gift: t('filterPane.gift'),
    themePack: t('filterPane.themePack'),
  }

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      {/* Toggle trigger */}
      <CollapsibleTrigger className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors cursor-pointer py-1">
        {t('filterPane.toggle')}
        {isOpen ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
      </CollapsibleTrigger>

      <CollapsibleContent>
        <div className="mt-2 rounded-lg border border-border bg-card p-3 space-y-3">
          {/* Row 1: Search input */}
          <Input
            type="text"
            placeholder={t('filterPane.searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => { setSearchQuery(e.target.value) }}
            className="h-8 text-sm"
          />

          {/* Row 2: Selected chips (always visible to preserve layout) */}
          <div className="flex flex-wrap items-center gap-1.5 min-h-[1.75rem]">
            <Badge
              variant="outline"
              className="gap-1 cursor-pointer hover:bg-destructive/20 transition-colors text-xs"
              onClick={() => {
                onFiltersChange({
                  keywords: [],
                  identityIds: [],
                  egoIds: [],
                  giftIds: [],
                  themePackIds: [],
                })
              }}
            >
              {t('common:reset')}
            </Badge>
            {selectedChips.map((chip) => (
              <Badge
                key={`${chip.category}-${chip.id}`}
                variant="secondary"
                className="gap-1 cursor-pointer hover:bg-destructive/20 transition-colors"
                onClick={() => { toggleItem(chip.category, chip.id) }}
              >
                {chip.label}
                <X className="size-3" />
              </Badge>
            ))}
          </div>

          {/* Category navigation buttons */}
          <div className="flex gap-1.5 flex-wrap">
            {FILTER_CATEGORIES.map((category) => (
              <button
                key={category}
                onClick={() => { scrollToCategory(category) }}
                className="selectable px-2.5 py-1 text-xs font-medium rounded-md bg-muted"
                data-selected={false}
              >
                {categoryLabels[category]}
                {lowerQuery && (
                  <span className="ml-1 text-muted-foreground">({matchCounts[category]})</span>
                )}
              </button>
            ))}
          </div>

          {/* Section content: scrollable area with category grids */}
          <div ref={scrollContainerRef} className="max-h-[30vh] overflow-y-auto p-0.5">
            <div className="space-y-4 pr-1.5">
              {/* Keywords section (always rendered, no suspense) */}
              <FilterSection
                ref={(el) => { sectionRefs.current.keywords = el }}
                title={categoryLabels.keywords}
                visible={matchCounts.keywords > 0 || !lowerQuery}
              >
                <div className="flex flex-wrap gap-1.5">
                  {getItemsForCategory('keywords').map((item) => (
                    <button
                      key={item.id}
                      onClick={() => { toggleItem('keywords', item.id) }}
                      className="selectable flex items-center gap-1.5 px-2 py-1 text-xs rounded-md bg-muted"
                      data-selected={isSelected('keywords', item.id)}
                      title={item.label}
                    >
                      <img
                        src={getKeywordIconPath(item.id)}
                        alt=""
                        className="size-4 object-contain"
                        loading="lazy"
                      />
                      <span>{item.label}</span>
                    </button>
                  ))}
                </div>
              </FilterSection>

              {/* Heavy sections (identity, ego, gift, themePack) — suspense-wrapped */}
              {isOpen && (
                <Suspense fallback={
                  <p className="text-xs text-muted-foreground py-2">
                    {t('filterPane.loading', { defaultValue: 'Loading filters...' })}
                  </p>
                }>
                  <HeavySections
                    searchQuery={lowerQuery}
                    getItemsForCategory={getItemsForCategory}
                    isSelected={isSelected}
                    toggleItem={toggleItem}
                    sectionRefs={sectionRefs}
                    categoryLabels={categoryLabels}
                    matchCounts={matchCounts}
                    onItemsReady={setHeavyItems}
                  />
                </Suspense>
              )}
            </div>
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

// ============================================================================
// HeavySections (inner, suspense-based)
// ============================================================================

interface HeavySectionsProps {
  searchQuery: string
  getItemsForCategory: (category: FilterCategory) => FilterItem[]
  isSelected: (category: FilterCategory, id: string) => boolean
  toggleItem: (category: FilterCategory, id: string) => void
  sectionRefs: React.MutableRefObject<Record<FilterCategory, HTMLDivElement | null>>
  categoryLabels: Record<FilterCategory, string>
  matchCounts: Record<FilterCategory, number>
  onItemsReady: (items: FilterItem[]) => void
}

function HeavySections({
  searchQuery,
  getItemsForCategory,
  isSelected,
  toggleItem,
  sectionRefs,
  categoryLabels,
  matchCounts,
  onItemsReady,
}: HeavySectionsProps) {
  const { t } = useTranslation(['sinnerNames'])

  // Suspense hooks — these suspend until data is loaded
  const identitySpec = useIdentityListSpec()
  const identityI18n = useIdentityListI18n()
  const egoSpec = useEGOListSpec()
  const egoI18n = useEGOListI18n()
  const egoGiftSpec = useEGOGiftListSpec()
  const egoGiftI18n = useEGOGiftListI18n()
  const { spec: themePackSpec, i18n: themePackI18n } = useThemePackListData()

  // Build heavy FilterItem[] from loaded data
  const heavyItems = useMemo((): FilterItem[] => {
    const items: FilterItem[] = []

    for (const id of Object.keys(identitySpec)) {
      const sinner = getSinnerFromId(id)
      const sinnerName = t(`sinnerNames:${sinner}`)
      const name = identityI18n[id] ?? id
      items.push({
        id,
        label: `${name} - ${sinnerName}`,
        category: 'identity',
        group: sinner,
      })
    }

    for (const id of Object.keys(egoSpec)) {
      const sinner = getSinnerFromId(id)
      const sinnerName = t(`sinnerNames:${sinner}`)
      const name = egoI18n[id] ?? id
      items.push({
        id,
        label: `${name} - ${sinnerName}`,
        category: 'ego',
        group: sinner,
      })
    }

    for (const id of Object.keys(egoGiftSpec)) {
      items.push({
        id,
        label: egoGiftI18n[id] ?? id,
        category: 'gift',
      })
    }

    for (const id of Object.keys(themePackSpec)) {
      items.push({
        id,
        label: themePackI18n[id]?.name ?? id,
        category: 'themePack',
      })
    }

    return items
  }, [identitySpec, identityI18n, egoSpec, egoI18n, egoGiftSpec, egoGiftI18n, themePackSpec, themePackI18n, t])

  // Report heavy items to parent so allItems/matchCounts update
  useEffect(() => {
    onItemsReady(heavyItems)
  }, [heavyItems, onItemsReady])

  return (
    <>
      {/* Identity section (grouped by sinner) */}
      <FilterSection
        ref={(el) => { sectionRefs.current.identity = el }}
        title={categoryLabels.identity}
        visible={matchCounts.identity > 0 || !searchQuery}
      >
        <SinnerGroupedGrid
          items={getItemsForCategory('identity')}
          isSelected={(id) => isSelected('identity', id)}
          onToggle={(id) => { toggleItem('identity', id) }}
          renderIcon={(id) => (
            <img
              src={getIdentityInfoImagePath(id)}
              alt=""
              className="size-full rounded object-cover"
              loading="lazy"
            />
          )}
        />
      </FilterSection>

      {/* EGO section (grouped by sinner) */}
      <FilterSection
        ref={(el) => { sectionRefs.current.ego = el }}
        title={categoryLabels.ego}
        visible={matchCounts.ego > 0 || !searchQuery}
      >
        <SinnerGroupedGrid
          items={getItemsForCategory('ego')}
          isSelected={(id) => isSelected('ego', id)}
          onToggle={(id) => { toggleItem('ego', id) }}
          renderIcon={(id) => (
            <img
              src={getEGOImagePath(id)}
              alt=""
              className="size-full rounded object-cover"
              loading="lazy"
            />
          )}
        />
      </FilterSection>

      {/* EGO Gift section */}
      <FilterSection
        ref={(el) => { sectionRefs.current.gift = el }}
        title={categoryLabels.gift}
        visible={matchCounts.gift > 0 || !searchQuery}
      >
        <div className="flex flex-wrap gap-1.5">
          {getItemsForCategory('gift').map((item) => (
            <button
              key={item.id}
              onClick={() => { toggleItem('gift', item.id) }}
              className="selectable size-10 rounded overflow-hidden p-0"
              data-selected={isSelected('gift', item.id)}
              title={item.label}
            >
              <img
                src={getEGOGiftIconPath(item.id)}
                alt=""
                className="size-full object-cover"
                loading="lazy"
              />
            </button>
          ))}
        </div>
      </FilterSection>

      {/* Theme Pack section */}
      <FilterSection
        ref={(el) => { sectionRefs.current.themePack = el }}
        title={categoryLabels.themePack}
        visible={matchCounts.themePack > 0 || !searchQuery}
      >
        <div className="flex flex-wrap gap-1.5">
          {getItemsForCategory('themePack').map((item) => (
            <button
              key={item.id}
              onClick={() => { toggleItem('themePack', item.id) }}
              className="selectable size-14 rounded overflow-hidden p-0"
              data-selected={isSelected('themePack', item.id)}
              title={item.label}
            >
              <img
                src={getThemePackImagePath(item.id)}
                alt=""
                className="size-full object-cover"
                loading="lazy"
              />
            </button>
          ))}
        </div>
      </FilterSection>
    </>
  )
}

// ============================================================================
// Subcomponents
// ============================================================================

/**
 * Filter section wrapper with title and visibility control
 */

interface FilterSectionProps {
  title: string
  visible: boolean
  children: React.ReactNode
}

const FilterSection = forwardRef<HTMLDivElement, FilterSectionProps>(
  function FilterSection({ title, visible, children }, ref) {
    if (!visible) return null

    return (
      <div ref={ref}>
        <h4 className="text-xs font-semibold text-muted-foreground mb-2">{title}</h4>
        {children}
      </div>
    )
  }
)

/**
 * Grid of icon buttons grouped by sinner name.
 * Used for Identity and EGO categories.
 */
interface SinnerGroupedGridProps {
  items: FilterItem[]
  isSelected: (id: string) => boolean
  onToggle: (id: string) => void
  renderIcon: (id: string) => React.ReactNode
}

function SinnerGroupedGrid({ items, isSelected, onToggle, renderIcon }: SinnerGroupedGridProps) {
  const { t } = useTranslation(['sinnerNames'])

  // Group items by sinner, preserving SINNERS order
  const grouped = useMemo(() => {
    const byGroup = new Map<string, FilterItem[]>()
    for (const item of items) {
      const group = item.group ?? 'Unknown'
      if (!byGroup.has(group)) byGroup.set(group, [])
      byGroup.get(group)!.push(item)
    }
    // Return in SINNERS order
    const result: { sinner: string; items: FilterItem[] }[] = []
    for (const sinner of SINNERS) {
      const sinnerItems = byGroup.get(sinner)
      if (sinnerItems && sinnerItems.length > 0) {
        result.push({ sinner, items: sinnerItems })
      }
    }
    return result
  }, [items])

  if (grouped.length === 0) return null

  return (
    <div className="space-y-2">
      {grouped.map(({ sinner, items: sinnerItems }) => (
        <div key={sinner}>
          <div className="flex items-center gap-1.5 mb-1">
            <img
              src={getSinnerIconPath(sinner)}
              alt=""
              className="size-4"
              loading="lazy"
            />
            <span className="text-xs text-muted-foreground">
              {t(`sinnerNames:${sinner}`)}
            </span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {sinnerItems.map((item) => (
              <button
                key={item.id}
                onClick={() => { onToggle(item.id) }}
                className="selectable size-10 rounded overflow-hidden p-0"
                data-selected={isSelected(item.id)}
                title={item.label}
              >
                {renderIcon(item.id)}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
