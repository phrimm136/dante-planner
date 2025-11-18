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
      <div className="flex gap-1 border rounded">
        <button
          onClick={() => onSortModeChange('tier-first')}
          className={`px-3 py-1 text-sm transition-colors ${
            sortMode === 'tier-first'
              ? 'bg-blue-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          {t('sorter.tierFirst', 'tier|keyword')}
        </button>
        <button
          onClick={() => onSortModeChange('keyword-first')}
          className={`px-3 py-1 text-sm transition-colors ${
            sortMode === 'keyword-first'
              ? 'bg-blue-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          {t('sorter.keywordFirst', 'keyword|tier')}
        </button>
      </div>
    </div>
  )
}
