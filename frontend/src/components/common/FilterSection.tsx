interface FilterSectionProps {
  title: string
  children: React.ReactNode
  defaultExpanded?: boolean
  activeCount?: number
}

/**
 * Filter section wrapper for FilterSidebar
 * Shows title with children always visible (no collapsing)
 *
 * Pattern: Simplified from previous Collapsible-based implementation
 */
export function FilterSection({
  title,
  children,
}: FilterSectionProps) {
  return (
    <div>
      <div className="px-1 py-1.5 text-sm font-medium text-foreground">
        {title}
      </div>
      <div className="pt-1 pb-0.5">{children}</div>
    </div>
  )
}
