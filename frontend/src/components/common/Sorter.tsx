import { useTranslation } from 'react-i18next'

export type SortMode = 'tier-first' | 'keyword-first'

interface SorterProps {
  sortMode: SortMode
  onSortModeChange: (mode: SortMode) => void
}

export function Sorter({ sortMode, onSortModeChange }: SorterProps) {
  const { t } = useTranslation()

  return (
    <div className="flex items-center gap-2">
      <span className="text-sm font-medium">Sort by:</span>
      <div className="flex gap-1 border rounded overflow-hidden">
        <button
          onClick={() => { onSortModeChange('tier-first'); }}
          className="selectable px-3 py-1 text-sm bg-card"
          data-selected={sortMode === 'tier-first'}
        >
          {t('sorter.tierFirst', 'tier|keyword')}
        </button>
        <button
          onClick={() => { onSortModeChange('keyword-first'); }}
          className="selectable px-3 py-1 text-sm bg-card"
          data-selected={sortMode === 'keyword-first'}
        >
          {t('sorter.keywordFirst', 'keyword|tier')}
        </button>
      </div>
    </div>
  )
}
