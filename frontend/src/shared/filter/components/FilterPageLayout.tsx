import { FilterSidebar } from './FilterSidebar'

interface FilterPageLayoutProps {
  filterContent?: React.ReactNode
  children: React.ReactNode
  searchBar?: React.ReactNode
  activeFilterCount?: number
  onResetAll?: () => void
  primaryFilters?: React.ReactNode
  secondaryFilters?: React.ReactNode
}

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
      <FilterSidebar
        activeFilterCount={activeFilterCount}
        onResetAll={onResetAll}
        primaryFilters={primaryFilters}
        secondaryFilters={secondaryFilters}
        searchBar={searchBar}
      >
        {filterContent ?? (
          <>
            {primaryFilters}
            {secondaryFilters}
          </>
        )}
      </FilterSidebar>

      <div className="flex-1 min-w-0">{children}</div>
    </div>
  )
}
