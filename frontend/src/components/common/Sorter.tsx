import { useTranslation } from 'react-i18next'

export type SortMode = 'tier-first' | 'keyword-first'

interface SorterProps {
  sortMode: SortMode
  onSortModeChange: (mode: SortMode) => void
}

export function Sorter({ sortMode, onSortModeChange }: SorterProps) {
  const { t } = useTranslation('database')

  return (
    <div className="bg-card border border-border rounded-md h-14 flex items-center gap-2 px-2">
      <span className="text-sm font-medium shrink-0">{t('sorter.label')}</span>
      <div className="flex gap-2">
        <button
          onClick={() => { onSortModeChange('tier-first'); }}
          data-selected={sortMode === 'tier-first'}
          className="selectable shrink-0 px-3 h-8 rounded-md border border-border text-sm"
        >
          {t('sorter.byTier')}
        </button>
        <button
          onClick={() => { onSortModeChange('keyword-first'); }}
          data-selected={sortMode === 'keyword-first'}
          className="selectable shrink-0 px-3 h-8 rounded-md border border-border text-sm"
        >
          {t('sorter.byKeyword')}
        </button>
      </div>
    </div>
  )
}
