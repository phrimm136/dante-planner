import { Suspense, type ComponentType, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'
import { FilterSection } from './FilterSection'

export interface FilterControlProps<TSelection> {
  selected: TSelection
  onSelectionChange: (next: TSelection) => void
}

export interface FilterSectionEntry {
  key: string
  titleKey: string
  titleFallback?: string
  activeCount: number
  suspense?: boolean
  control: ReactNode
}

interface FilterSectionSpec<TSelection extends { readonly size: number }, TProps> {
  key: string
  titleKey: string
  titleFallback?: string
  Component: ComponentType<TProps>
  selected: TSelection
  onSelectionChange: (next: TSelection) => void
  props?: Omit<TProps, keyof FilterControlProps<TSelection>>
  suspense?: boolean
}

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
    ...(titleFallback !== undefined && { titleFallback }),
    activeCount: selected.size,
    ...(suspense !== undefined && { suspense }),
    control: <Component {...controlProps} />,
  }
}

interface FilterSectionListProps {
  sections: readonly FilterSectionEntry[]
}

export function FilterSectionList({ sections }: FilterSectionListProps) {
  const { t } = useTranslation('database')

  return (
    <>
      {sections.map((section) => (
        <FilterSection
          key={section.key}
          title={t(section.titleKey, { defaultValue: section.titleFallback })}
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
