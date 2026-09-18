import { useTranslation } from 'react-i18next'
import { Upload, Download, Edit } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface DeckBuilderActionBarProps {
  onImport?: (() => void) | undefined
  onExport?: (() => void) | undefined
  onResetOrder?: (() => void) | undefined
  showEditDeck?: boolean
  onEditDeck?: (() => void) | undefined
  trackerMode?: boolean
  onResetToInitial?: (() => void) | undefined
}

export const DeckBuilderActionBar = function DeckBuilderActionBar({
  onImport,
  onExport,
  onResetOrder,
  showEditDeck = false,
  onEditDeck,
  trackerMode = false,
  onResetToInitial,
}: DeckBuilderActionBarProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <div className="flex flex-wrap shrink-0 justify-end gap-2">
      {showEditDeck && onEditDeck && (
        <Button variant="default" size="sm" onClick={onEditDeck}>
          <Edit className="w-4 h-4" />
          {t('deckBuilder.editDeck')}
        </Button>
      )}
      <Button variant="outline" size="sm" onClick={onImport}>
        <Download className="w-4 h-4" />
        {t('deckBuilder.import')}
      </Button>
      <Button variant="outline" size="sm" onClick={onExport}>
        <Upload className="w-4 h-4" />
        {t('deckBuilder.export')}
      </Button>
      <Button variant="outline" size="sm" onClick={onResetOrder}>
        {t('deckBuilder.resetOrder')}
      </Button>
      {trackerMode && onResetToInitial && (
        <Button variant="outline" size="sm" onClick={onResetToInitial}>
          {t('deckBuilder.resetToInitial', 'Reset to Initial')}
        </Button>
      )}
    </div>
  )
}
