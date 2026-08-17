import { useTranslation } from 'react-i18next'
import { Upload, Download, Edit } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface DeckBuilderActionBarProps {
  onImport?: (() => void) | undefined
  onExport?: (() => void) | undefined
  onResetOrder?: (() => void) | undefined
  /** Show "Edit Deck" button (Summary view only) */
  showEditDeck?: boolean
  /** Callback when "Edit Deck" is clicked */
  onEditDeck?: (() => void) | undefined
  /** Tracker mode flag - shows "Reset to Initial" instead of "Reset Order" */
  trackerMode?: boolean
  /** Reset to planner's original deployment (tracker mode only) */
  onResetToInitial?: (() => void) | undefined
}

/**
 * Shared action bar for DeckBuilder Summary and Pane
 * Contains Import, Export, Reset Order buttons
 * Optionally shows "Edit Deck" button in Summary view
 *
 * memo: render only depends on boolean flags; callback identities
 * change across parent renders but behavior is stable.
 */
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
