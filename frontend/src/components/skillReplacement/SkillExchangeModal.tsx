import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { SkillEADisplay } from './SkillEADisplay'
import { SkillExchangePane, ResetPane } from './SkillExchangePane'
import { OFFENSIVE_SKILL_SLOTS } from '@/lib/constants'
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
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.skillReplacement.title')} - {t(`sinnerNames:${sinnerName}`)}
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-8">
          {/* Top: Current EA display */}
          <div className="flex flex-col gap-4">
            <h3 className="text-sm font-medium text-muted-foreground">
              {t('pages.plannerMD.skillReplacement.currentSkills')}
            </h3>
            <div className="flex gap-3 justify-center">
              {OFFENSIVE_SKILL_SLOTS.map((slot) => (
                <SkillEADisplay
                  key={slot}
                  identityId={identityId}
                  skillSlot={slot}
                  attributeType={skillInfos[slot].attributeType}
                  atkType={skillInfos[slot].atkType}
                  ea={skillEA[slot]}
                  currentEA={currentEA?.[slot]}
                />
              ))}
            </div>
          </div>

          {/* Bottom: Exchange options */}
          <div className="flex flex-col gap-4">
            <h3 className="text-sm font-medium text-muted-foreground">
              {t('pages.plannerMD.skillReplacement.exchangeOptions')}
            </h3>
            <div className="flex flex-col gap-2">
              {EXCHANGE_PAIRS.map(([source, target]) => {
                const ea = currentEA || skillEA
                return (
                  <SkillExchangePane
                    key={`${source}-${target}`}
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
                )
              })}
              <ResetPane onClick={handleReset} />
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
