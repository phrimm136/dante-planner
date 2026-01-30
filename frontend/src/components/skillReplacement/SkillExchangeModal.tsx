import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { SkillEADisplay } from './SkillEADisplay'
import { SkillExchangePane } from './SkillExchangePane'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { OFFENSIVE_SKILL_SLOTS, CARD_GRID } from '@/lib/constants'
import type { OffensiveSkillSlot } from '@/lib/constants'
import type { SkillEAState, SkillInfo } from '@/types/DeckTypes'

interface SkillExchangeModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  sinnerName: string
  identityId: string
  skillInfos: [SkillInfo, SkillInfo, SkillInfo] // S1, S2, S3
  skillEA: SkillEAState
  currentEA?: SkillEAState
  onExchange: (sourceSlot: OffensiveSkillSlot, targetSlot: OffensiveSkillSlot) => void
  onReset: () => void
}

/**
 * Exchange pairs allowed by the spec: S1→S2, S2→S3, S1→S3
 */
const EXCHANGE_PAIRS: [OffensiveSkillSlot, OffensiveSkillSlot][] = [
  [0, 1], // S1 → S2
  [1, 2], // S2 → S3
  [0, 2], // S1 → S3
]

/**
 * SkillExchangeModal - Dialog for skill EA exchange
 *
 * Layout:
 * - Left: Current EA display for each skill
 * - Right: Exchange options (S1→S2, S2→S3, S1→S3) and Reset button
 */
export function SkillExchangeModal({
  open,
  onOpenChange,
  sinnerName,
  identityId,
  skillInfos,
  skillEA,
  currentEA,
  onExchange,
  onReset,
}: SkillExchangeModalProps) {
  const { t } = useTranslation(['planner', 'common', 'sinnerNames'])

  // Breakpoint detection for scaling


  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  const handleExchange = (source: OffensiveSkillSlot, target: OffensiveSkillSlot) => {
    const ea = currentEA || skillEA
    if (ea[source] > 0) {
      onExchange(source, target)
    }
  }

  const handleReset = () => {
    onReset()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[calc(100%-0.5rem)] sm:max-w-[400px] lg:max-w-[480px] max-h-[90vh] flex flex-col" showCloseButton={false}>
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <div className="flex items-center gap-4 flex-wrap">
            <DialogTitle>
              {t('pages.plannerMD.skillReplacement.title')} - {t(`sinnerNames:${sinnerName}`)}
            </DialogTitle>
            <div className="flex gap-2 ml-auto">
              <Button
                variant="outline"
                size="sm"
                onClick={handleReset}
              >
                {t('common:reset')}
              </Button>
              <Button size="sm" onClick={() => { onOpenChange(false); }}>
                {t('common:done')}
              </Button>
            </div>
          </div>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto py-4">
          <div className="flex flex-col gap-4">
            {/* Top: Current EA display */}
            <div className="flex flex-col gap-2">
            <h3 className="text-sm font-medium text-muted-foreground">
              {t('pages.plannerMD.skillReplacement.currentSkills')}
            </h3>
            <div className="flex gap-3 justify-center">
              {OFFENSIVE_SKILL_SLOTS.map((slot) => (
                <ScaledCardWrapper
                  key={slot}
                  mobileScale={mobileScale}
                  cardWidth={CARD_GRID.WIDTH.SKILL_IMAGE}
                  cardHeight={CARD_GRID.HEIGHT.SKILL_IMAGE}
                >
                  <SkillEADisplay
                    identityId={identityId}
                    skillSlot={slot}
                    attributeType={skillInfos[slot].attributeType}
                    atkType={skillInfos[slot].atkType}
                    ea={skillEA[slot]}
                    currentEA={currentEA?.[slot]}
                  />
                </ScaledCardWrapper>
              ))}
            </div>
          </div>

          {/* Bottom: Exchange options */}
          <div className="flex flex-col gap-4">
            <h3 className="text-sm font-medium text-muted-foreground">
              {t('pages.plannerMD.skillReplacement.exchangeOptions')}
            </h3>
            <div className="flex flex-col gap-2 items-center">
              {EXCHANGE_PAIRS.map(([source, target]) => {
                const ea = currentEA || skillEA
                return (
                  <ScaledCardWrapper
                    key={`${source}-${target}`}
                    mobileScale={mobileScale}
                    cardWidth={CARD_GRID.WIDTH.SKILL_EXCHANGE}
                    cardHeight={CARD_GRID.HEIGHT.SKILL_EXCHANGE}
                  >
                    <SkillExchangePane
                      identityId={identityId}
                      sourceSlot={source}
                      targetSlot={target}
                      sourceAttributeType={skillInfos[source].attributeType}
                      targetAttributeType={skillInfos[target].attributeType}
                      sourceAtkType={skillInfos[source].atkType}
                      targetAtkType={skillInfos[target].atkType}
                      sourceEA={ea[source]}
                      onClick={() => { handleExchange(source, target); }}
                    />
                  </ScaledCardWrapper>
                )
              })}
            </div>
          </div>
        </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
