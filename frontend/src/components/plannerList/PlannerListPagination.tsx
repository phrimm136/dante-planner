import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'

interface PlannerListPaginationProps {
  /** Current page number (0-indexed) */
  currentPage: number
  /** Total number of pages */
  totalPages: number
  /** Callback when page changes */
  onPageChange: (page: number) => void
}

/**
 * Maximum number of page buttons to show (excluding prev/next and ellipsis)
 */
const MAX_VISIBLE_PAGES = 5

/**
 * Calculate which page numbers to display.
 * Shows ellipsis when there are too many pages.
 */
function getPageNumbers(currentPage: number, totalPages: number): (number | 'ellipsis')[] {
  if (totalPages <= MAX_VISIBLE_PAGES) {
    return Array.from({ length: totalPages }, (_, i) => i)
  }

  const pages: (number | 'ellipsis')[] = []
  const halfVisible = Math.floor(MAX_VISIBLE_PAGES / 2)

  // Always show first page
  pages.push(0)

  // Calculate start and end of visible range
  let start = Math.max(1, currentPage - halfVisible)
  let end = Math.min(totalPages - 2, currentPage + halfVisible)

  // Adjust range to show MAX_VISIBLE_PAGES - 2 pages (excluding first and last)
  const rangeSize = end - start + 1
  const targetSize = MAX_VISIBLE_PAGES - 2

  if (rangeSize < targetSize) {
    if (start === 1) {
      end = Math.min(totalPages - 2, start + targetSize - 1)
    } else {
      start = Math.max(1, end - targetSize + 1)
    }
  }

  // Add ellipsis or page after first
  if (start > 1) {
    pages.push('ellipsis')
  }

  // Add middle pages
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }

  // Add ellipsis or page before last
  if (end < totalPages - 2) {
    pages.push('ellipsis')
  }

  // Always show last page
  if (totalPages > 1) {
    pages.push(totalPages - 1)
  }

  return pages
}

/**
 * Pagination wrapper for planner list.
 * Uses shadcn pagination with ellipsis for many pages.
 *
 * @example
 * const { page, setFilters } = usePlannerListFilters();
 * const { data } = usePlannerListData({ page, ... });
 *
 * <PlannerListPagination
 *   currentPage={page}
 *   totalPages={data.totalPages}
 *   onPageChange={(p) => setFilters({ page: p })}
 * />
 */
export function PlannerListPagination({
  currentPage,
  totalPages,
  onPageChange,
}: PlannerListPaginationProps) {
  // Don't render if only one page
  if (totalPages <= 1) {
    return null
  }

  const pageNumbers = getPageNumbers(currentPage, totalPages)
  const hasPrevious = currentPage > 0
  const hasNext = currentPage < totalPages - 1

  return (
    <Pagination>
      <PaginationContent>
        {/* Previous button */}
        <PaginationItem>
          <PaginationPrevious
            onClick={() => { if (hasPrevious) onPageChange(currentPage - 1) }}
            aria-disabled={!hasPrevious}
            className={!hasPrevious ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
          />
        </PaginationItem>

        {/* Page numbers */}
        {pageNumbers.map((page, index) => (
          <PaginationItem key={page === 'ellipsis' ? `ellipsis-${index}` : page}>
            {page === 'ellipsis' ? (
              <PaginationEllipsis />
            ) : (
              <PaginationLink
                onClick={() => { onPageChange(page) }}
                isActive={page === currentPage}
                className="cursor-pointer"
              >
                {page + 1}
              </PaginationLink>
            )}
          </PaginationItem>
        ))}

        {/* Next button */}
        <PaginationItem>
          <PaginationNext
            onClick={() => { if (hasNext) onPageChange(currentPage + 1) }}
            aria-disabled={!hasNext}
            className={!hasNext ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  )
}
