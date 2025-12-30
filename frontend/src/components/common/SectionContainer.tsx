import { cn } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'

interface SectionContainerProps {
  /** Section title - rendered as h2 */
  title: string
  /** Optional counter/caption on right side */
  caption?: React.ReactNode
  /** Content inside the container */
  children: React.ReactNode
  /** Additional className for the outer wrapper */
  className?: string
  /** Set to true to skip the container background (for custom layouts) */
  noContainer?: boolean
}

/**
 * @deprecated Use PlannerSection instead for planner pages.
 * SectionContainer will be removed in a future release.
 *
 * Wrapper component for consistent section styling.
 * Uses SECTION_STYLES tokens for headers, captions, and content wrappers.
 *
 * Pattern: AssociationDropdown.tsx container styling with SECTION_STYLES tokens
 */
export function SectionContainer({
  title,
  caption,
  children,
  className,
  noContainer = false,
}: SectionContainerProps) {
  return (
    <section className={cn(SECTION_STYLES.SPACING.content, className)}>
      {/* Header Row */}
      <div className="flex items-center justify-between">
        <h2 className={SECTION_STYLES.TEXT.header}>{title}</h2>
        {caption && (
          <span className={SECTION_STYLES.TEXT.caption}>{caption}</span>
        )}
      </div>

      {/* Content Container */}
      {noContainer ? (
        children
      ) : (
        <div className={SECTION_STYLES.container}>
          {children}
        </div>
      )}
    </section>
  )
}
