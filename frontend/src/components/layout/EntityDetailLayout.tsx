import type { ReactNode } from 'react'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { ENTITY_DETAIL_LAYOUT, LG_BREAKPOINT_PX, SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface EntityDetailLayoutProps {
  header: ReactNode
  metadata: ReactNode
  selector: ReactNode
  children: ReactNode
}

export function EntityDetailLayout({
  header,
  metadata,
  selector,
  children,
}: EntityDetailLayoutProps) {
  const isMobile = useIsBreakpoint('max', LG_BREAKPOINT_PX)

  const sections = (
    <div data-slot="detail-sections" className={SECTION_STYLES.SPACING.section}>
      {children}
    </div>
  )
  const selectorRow = <div data-slot="detail-selector">{selector}</div>

  if (isMobile) {
    return (
      <div className={cn(ENTITY_DETAIL_LAYOUT.PAGE_MOBILE, SECTION_STYLES.SPACING.section)}>
        <div data-slot="detail-header">{header}</div>
        <div data-slot="detail-metadata">{metadata}</div>
        {selectorRow}
        {sections}
      </div>
    )
  }

  return (
    <div className={cn(SECTION_STYLES.LAYOUT.page, SECTION_STYLES.SPACING.section)}>
      <div data-slot="detail-header-row" className={ENTITY_DETAIL_LAYOUT.HEADER_ROW}>
        <div data-slot="detail-header" className={ENTITY_DETAIL_LAYOUT.HEADER_COLUMN}>
          {header}
        </div>
        <div data-slot="detail-metadata" className={ENTITY_DETAIL_LAYOUT.METADATA_COLUMN}>
          {metadata}
        </div>
      </div>
      {selectorRow}
      {sections}
    </div>
  )
}
