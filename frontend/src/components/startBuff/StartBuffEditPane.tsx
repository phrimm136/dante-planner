import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import type { MDVersion } from '@/hooks/useStartBuffData'
import { useStartBuffSelection } from '@/hooks/useStartBuffSelection'
import { StartBuffCard } from './StartBuffCard'

interface StartBuffEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  onSelectionChange: (buffIds: Set<number>) => void
}

/**
 * Dialog for editing start buff selection
 * Renders cards directly without intermediate Grid component
 */
export function StartBuffEditPane({
  open,
  onOpenChange,
  mdVersion,
  selectedBuffIds,
  onSelectionChange,
}: StartBuffEditPaneProps) {
  const { t } = useTranslation()
  const { buffs, i18n, battleKeywords, displayBuffs, handleSelect } =
    useStartBuffSelection(mdVersion, selectedBuffIds, onSelectionChange)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] duration-100">
        <DialogHeader>
          <DialogTitle>{t('pages.plannerMD.startBuffs')}</DialogTitle>
        </DialogHeader>

        <div className="overflow-x-auto">
          <div className="bg-muted w-full max-w-full overflow-x-auto scrollbar-hide">
            <div className="grid grid-cols-5 gap-1 min-w-max">
              {displayBuffs.map((buff) => {
                const currentBuffId = Number(buff.id)
                const isSelected = selectedBuffIds.has(currentBuffId)

                return (
                  <StartBuffCard
                    key={buff.baseId}
                    buff={buff}
                    allBuffs={buffs}
                    i18n={i18n}
                    battleKeywords={battleKeywords}
                    isSelected={isSelected}
                    onSelect={handleSelect}
                    viewMode={false}
                  />
                )
              })}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button onClick={() => { onOpenChange(false) }}>
            {t('common.done')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
