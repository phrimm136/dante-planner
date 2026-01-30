import { useMemo, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { CARD_GRID, type MDVersion } from '@/lib/constants'
import { useStartBuffSelection } from '@/hooks/useStartBuffSelection'
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { StartBuffCard } from './StartBuffCard'

interface StartBuffEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: MDVersion
}

/**
 * Dialog for editing start buff selection
 * Renders cards directly without intermediate Grid component
 */
export function StartBuffEditPane({
  open,
  onOpenChange,
  mdVersion,
}: StartBuffEditPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Store state
  const selectedBuffIds = usePlannerEditorStore((s) => s.selectedBuffIds)
  const setSelectedBuffIds = usePlannerEditorStore((s) => s.setSelectedBuffIds)

  const { buffs, i18n, battleKeywords, displayBuffs, handleSelect } =
    useStartBuffSelection(mdVersion, selectedBuffIds, setSelectedBuffIds)

  // Breakpoint detection for scaling
  const [isDesktop, setIsDesktop] = useState(
    typeof window !== 'undefined' && window.innerWidth >= CARD_GRID.LG_BREAKPOINT
  )

  useEffect(() => {
    const handleResize = () => {
      setIsDesktop(window.innerWidth >= CARD_GRID.LG_BREAKPOINT)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Calculate scale and dimensions
  const mobileScale = CARD_GRID.MOBILE_SCALE.DENSE
  const scale = isDesktop ? 1 : mobileScale
  const scaledWidth = CARD_GRID.WIDTH.START_BUFF * scale
  const scaledHeight = CARD_GRID.HEIGHT.START_BUFF * scale

  // Calculate total star cost of selected buffs
  const totalCost = useMemo(() => {
    return displayBuffs
      .filter((buff) => selectedBuffIds.has(Number(buff.id)))
      .reduce((sum, buff) => sum + buff.cost, 0)
  }, [displayBuffs, selectedBuffIds])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <div className="flex items-center gap-4 flex-wrap">
            <DialogTitle>{t('pages.plannerMD.startBuffs')}</DialogTitle>
            <div className="flex items-center gap-4 ml-auto">
              <StarlightCostDisplay cost={totalCost} size="lg" />
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => { setSelectedBuffIds(new Set()) }}
                >
                  {t('common:reset')}
                </Button>
                <Button size="sm" onClick={() => { onOpenChange(false) }}>
                  {t('common:done')}
                </Button>
              </div>
            </div>
          </div>
        </DialogHeader>

        <div className="w-full overflow-x-auto scrollbar-hide">
          <div
            className="bg-muted grid gap-1 w-max"
            style={{
              gridTemplateColumns: `repeat(5, ${scaledWidth}px)`,
              gridAutoRows: `${scaledHeight}px`,
            }}
          >
            {displayBuffs.map((buff) => {
              const currentBuffId = Number(buff.id)
              const isSelected = selectedBuffIds.has(currentBuffId)

              return (
                <ScaledCardWrapper
                  key={buff.baseId}
                  mobileScale={mobileScale}
                  cardWidth={CARD_GRID.WIDTH.START_BUFF}
                  cardHeight={CARD_GRID.HEIGHT.START_BUFF}
                >
                  <StartBuffCard
                    buff={buff}
                    allBuffs={buffs}
                    i18n={i18n}
                    battleKeywords={battleKeywords}
                    isSelected={isSelected}
                    onSelect={handleSelect}
                    mdVersion={mdVersion}
                  />
                </ScaledCardWrapper>
              )
            })}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
