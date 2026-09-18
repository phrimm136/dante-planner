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
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
}

const MAX_VISIBLE_PAGES = 5

function getPageNumbers(currentPage: number, totalPages: number): (number | 'ellipsis')[] {
  if (totalPages <= MAX_VISIBLE_PAGES) {
    return Array.from({ length: totalPages }, (_, i) => i)
  }

  const pages: (number | 'ellipsis')[] = []
  const halfVisible = Math.floor(MAX_VISIBLE_PAGES / 2)

  pages.push(0)

  let start = Math.max(1, currentPage - halfVisible)
  let end = Math.min(totalPages - 2, currentPage + halfVisible)

  const rangeSize = end - start + 1
  const targetSize = MAX_VISIBLE_PAGES - 2

  if (rangeSize < targetSize) {
    if (start === 1) {
      end = Math.min(totalPages - 2, start + targetSize - 1)
    } else {
      start = Math.max(1, end - targetSize + 1)
    }
  }

  if (start > 1) {
    pages.push('ellipsis')
  }

  for (let i = start; i <= end; i++) {
    pages.push(i)
  }

  if (end < totalPages - 2) {
    pages.push('ellipsis')
  }

  if (totalPages > 1) {
    pages.push(totalPages - 1)
  }

  return pages
}

export function PlannerListPagination({
  currentPage,
  totalPages,
  onPageChange,
}: PlannerListPaginationProps) {
  if (totalPages <= 1) {
    return null
  }

  const pageNumbers = getPageNumbers(currentPage, totalPages)
  const hasPrevious = currentPage > 0
  const hasNext = currentPage < totalPages - 1

  return (
    <Pagination>
      <PaginationContent>
        <PaginationItem>
          <PaginationPrevious
            onClick={() => {
              if (hasPrevious) onPageChange(currentPage - 1)
            }}
            aria-disabled={!hasPrevious}
            className={!hasPrevious ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
          />
        </PaginationItem>

        {pageNumbers.map((page, index) => (
          <PaginationItem key={page === 'ellipsis' ? `ellipsis-${index}` : page}>
            {page === 'ellipsis' ? (
              <PaginationEllipsis />
            ) : (
              <PaginationLink
                onClick={() => {
                  onPageChange(page)
                }}
                isActive={page === currentPage}
                className="cursor-pointer"
              >
                {page + 1}
              </PaginationLink>
            )}
          </PaginationItem>
        ))}

        <PaginationItem>
          <PaginationNext
            onClick={() => {
              if (hasNext) onPageChange(currentPage + 1)
            }}
            aria-disabled={!hasNext}
            className={!hasNext ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  )
}
