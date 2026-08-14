import { Suspense, type ComponentType, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'
import { FilterSection } from './FilterSection'

/** The prop pair every filter control exposes, whatever it selects over. */
export interface FilterControlProps<TSelection> {
  selected: TSelection
  onSelectionChange: (next: TSelection) => void
}

/** One titled filter section, already bound to its slice of the page's filter state. */
export interface FilterSectionEntry {
  key: string
  titleKey: string
  titleFallback?: string
  activeCount: number
  defaultExpanded?: boolean
  suspense?: boolean
  control: ReactNode
}

interface FilterSectionSpec<TSelection extends { readonly size: number }, TProps> {
  /** Section identity — the filter state key it drives. */
  key: string
  /** Title key in the `database` namespace. */
  titleKey: string
  titleFallback?: string
  Component: ComponentType<TProps>
  selected: TSelection
  onSelectionChange: (next: TSelection) => void
  /** Everything the control needs beyond the selection pair. */
  props?: Omit<TProps, keyof FilterControlProps<TSelection>>
  defaultExpanded?: boolean
  /** Wraps the control in the shared dropdown-loading fallback. */
  suspense?: boolean
}

/**
 * Binds one filter control to its selection, checking at the call site that the control
 * really accepts that selection type.
 *
 * @example
 * filterSection({
 *   key: 'selectedSinners',
 *   titleKey: 'filters.sinner',
 *   titleFallback: 'Sinner',
 *   Component: SinnerFilter,
 *   selected: filters.selectedSinners,
 *   onSelectionChange: setters.selectedSinners,
 * })
 */
export function filterSection<
  TSelection extends { readonly size: number },
  TProps extends FilterControlProps<TSelection>,
>({
  key,
  titleKey,
  titleFallback,
  Component,
  selected,
  onSelectionChange,
  props,
  defaultExpanded,
  suspense,
}: FilterSectionSpec<TSelection, TProps>): FilterSectionEntry {
  const controlProps = {
    ...props,
    selected,
    onSelectionChange,
  } as TProps

  return {
    key,
    titleKey,
    titleFallback,
    activeCount: selected.size,
    defaultExpanded,
    suspense,
    control: <Component {...controlProps} />,
  }
}

interface FilterSectionListProps {
  sections: readonly FilterSectionEntry[]
}

/**
 * Renders a page's filter sections: title, active count, and the control, with the
 * dropdown-loading fallback around the sections that suspend.
 */
export function FilterSectionList({ sections }: FilterSectionListProps) {
  const { t } = useTranslation('database')

  return (
    <>
      {sections.map((section) => (
        <FilterSection
          key={section.key}
          title={t(section.titleKey, { defaultValue: section.titleFallback })}
          defaultExpanded={section.defaultExpanded}
          activeCount={section.activeCount}
        >
          {section.suspense === true ? (
            <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
              {section.control}
            </Suspense>
          ) : (
            section.control
          )}
        </FilterSection>
      ))}
    </>
  )
}
