import { FilterSidebar } from './FilterSidebar'

interface FilterPageLayoutProps {
  /** Filter components to render inside the sidebar (desktop uses this) */
  filterContent: React.ReactNode
  /** Main content (e.g., list component) */
  children: React.ReactNode
  /** Optional search bar rendered above content */
  searchBar?: React.ReactNode
  /** Number of active filters for mobile badge */
  activeFilterCount?: number
  /** Callback to reset all filters */
  onResetAll?: () => void
  /** Primary filters always visible on mobile (Sinner, Keyword) */
  primaryFilters?: React.ReactNode
  /** Secondary filters shown when mobile expanded (Skill Attribute, Attack Type, etc.) */
  secondaryFilters?: React.ReactNode
}

/**
 * Responsive layout for list pages with filter sidebar
 *
 * Desktop (lg+): Sidebar left (280px) | Content right (flex-1)
 * Mobile (<lg): Inline expandable filter header at top
 *   - Collapsed: primaryFilters + Reset All + Expand button
 *   - Expanded: primaryFilters + secondaryFilters + Reset All + Collapse button
 *
 * Pattern: Named slots composition (like DetailPageLayout)
 * Reusable for Identity, EGO, and EGOGift pages
 */
export function FilterPageLayout({
  filterContent,
  children,
  searchBar,
  activeFilterCount = 0,
  onResetAll,
  primaryFilters,
  secondaryFilters,
}: FilterPageLayoutProps) {
  return (
    <div className="flex flex-col lg:flex-row gap-6">
      {/* Filter Sidebar - renders desktop sidebar (lg:block) and mobile expandable header (lg:hidden) */}
      <FilterSidebar
        activeFilterCount={activeFilterCount}
        onResetAll={onResetAll}
        primaryFilters={primaryFilters}
        secondaryFilters={secondaryFilters}
        searchBar={searchBar}
      >
        {filterContent}
      </FilterSidebar>

      {/* Main content area */}
      <div className="flex-1 min-w-0">
        {/* List content */}
        {children}
      </div>
    </div>
  )
}
