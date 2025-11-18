interface DetailPageLayoutProps {
  leftColumn: React.ReactNode
  rightColumn: React.ReactNode
}

/**
 * DetailPageLayout - Reusable two-column detail page layout
 *
 * Provides consistent responsive grid layout for detail pages
 */
export function DetailPageLayout({ leftColumn, rightColumn }: DetailPageLayoutProps) {
  return (
    <div className="container mx-auto p-8">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-6">{leftColumn}</div>
        <div className="space-y-6">{rightColumn}</div>
      </div>
    </div>
  )
}
