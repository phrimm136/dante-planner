interface FilterSectionProps {
  title: string
  children: React.ReactNode
  defaultExpanded?: boolean
  activeCount?: number
}

/**
 * Filter section wrapper for FilterSidebar
 * Shows title with optional active count in parentheses
 *
 * Pattern: Section with divider, count shown as "(n)" matching Reset button style
 */
export function FilterSection({
  title,
  children,
  activeCount = 0,
}: FilterSectionProps) {
  return (
    <div className="border-b border-border/50 pb-2 last:border-b-0 last:pb-0">
      <div className="px-1 py-1.5">
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
          {title}
          {activeCount > 0 && (
            <span className="ml-1 text-foreground">({activeCount})</span>
          )}
        </span>
      </div>
      <div className="pt-1 pb-0.5">{children}</div>
    </div>
  )
}
