import { useTranslation } from 'react-i18next'
import { Upload, Download, Edit } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface DeckBuilderActionBarProps {
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
  /** Show "Edit Deck" button (Summary view only) */
  showEditDeck?: boolean
  /** Callback when "Edit Deck" is clicked */
  onEditDeck?: () => void
}

/**
 * Shared action bar for DeckBuilder Summary and Pane
 * Contains Import, Export, Reset Order buttons
 * Optionally shows "Edit Deck" button in Summary view
 */
export function DeckBuilderActionBar({
  onImport,
  onExport,
  onResetOrder,
  showEditDeck = false,
  onEditDeck,
}: DeckBuilderActionBarProps) {
  const { t } = useTranslation()

  return (
    <div className="flex shrink-0 justify-end gap-2">
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
    </div>
  )
}
