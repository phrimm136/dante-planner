interface DetailLeftPanelProps {
  /** Content to display in the left panel */
  children: React.ReactNode
}

/**
 * DetailLeftPanel - Container for left column content on detail pages
 *
 * Provides consistent spacing and structure for:
 * - Identity: Header, status panels, resistance, traits
 * - EGO: Header, resource costs, info panels
 * - EGO Gift: Header, keywords, tier info
 *
 * Pattern: Follows DetailPageLayout.tsx container structure
 */
export function DetailLeftPanel({ children }: DetailLeftPanelProps) {
  return <div className="space-y-6">{children}</div>
}

export default DetailLeftPanel
