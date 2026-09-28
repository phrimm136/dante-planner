import { useState, startTransition } from 'react'
import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { DUNGEON_IDX, DIFFICULTY_LABELS, type DungeonIdx, type MDCategory } from '@/shared/gameData'
import { CARD_MOBILE_SCALE_DENSE, DIFFICULTY_COLORS } from '@/lib/constants'
import { THEME_PACK_GEOMETRY } from '@/shared/cardLayout'
import { ThemePackViewer } from './ThemePackViewer'
import { ThemePackExclusiveGifts } from './ThemePackExclusiveGifts'
import { offeredFloorDifficulties } from '../../lib/plannerRules'
import type { ThemePackListType, ThemePackSpec } from '@/pages/themePack'

interface ThemePackSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number
  earlierFloorsAllNormal: boolean
  themePackList: ThemePackListType
  themePackI18n: Record<string, { name: string; specialName?: string | undefined }>
  onSelect: (packId: string, difficulty: DungeonIdx) => void
  usedThemePackIds: Set<string>
  category: MDCategory
}

function filterThemePacks(
  themePackList: ThemePackListType,
  floorNumber: number,
  difficulty: DungeonIdx,
  usedThemePackIds: Set<string>,
): { id: string; entry: ThemePackSpec }[] {
  const result: { id: string; entry: ThemePackSpec }[] = []

  // 1 → 0, 2 → 1, 3 → 2, 4 → 3, 5-10 → 4
  const floorIndex = floorNumber <= 4 ? floorNumber - 1 : 4

  for (const [id, entry] of Object.entries(themePackList)) {
    if (usedThemePackIds.has(id)) {
      continue
    }

    for (const condition of entry.exceptionConditions) {
      if (condition.dungeonIdx !== difficulty) continue

      // For extreme (dungeonIdx: 3), no selectableFloors means all 11-15F
      if (difficulty === DUNGEON_IDX.EXTREME) {
        if (!condition.selectableFloors) {
          result.push({ id, entry })
          break
        }
      } else {
        if (condition.selectableFloors?.includes(floorIndex)) {
          result.push({ id, entry })
          break
        }
      }
    }
  }

  return result
}

export function ThemePackSelectorPane({
  open,
  onOpenChange,
  floorNumber,
  earlierFloorsAllNormal,
  themePackList,
  themePackI18n,
  onSelect,
  usedThemePackIds,
  category,
}: ThemePackSelectorPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  const availableDifficulties = offeredFloorDifficulties(
    category,
    floorNumber - 1,
    earlierFloorsAllNormal,
  )

  const [chosenDifficulty, setSelectedDifficulty] = useState<DungeonIdx | undefined>(undefined)
  const selectedDifficulty =
    chosenDifficulty !== undefined && availableDifficulties.includes(chosenDifficulty)
      ? chosenDifficulty
      : availableDifficulties[0]

  const handlePackSelect = (packId: string) => {
    if (selectedDifficulty === undefined) return
    startTransition(() => {
      onSelect(packId, selectedDifficulty)
      onOpenChange(false)
    })
  }

  const getDifficultyLabel = (idx: DungeonIdx): string => {
    switch (idx) {
      case DUNGEON_IDX.NORMAL:
        return DIFFICULTY_LABELS.NORMAL
      case DUNGEON_IDX.HARD:
        return DIFFICULTY_LABELS.HARD
      case DUNGEON_IDX.EXTREME:
        return DIFFICULTY_LABELS.EXTREME_MIRROR
      default:
        return 'UNKNOWN'
    }
  }

  const getDifficultyColor = (idx: DungeonIdx): string | undefined => {
    switch (idx) {
      case DUNGEON_IDX.NORMAL:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.NORMAL]
      case DUNGEON_IDX.HARD:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.HARD]
      case DUNGEON_IDX.EXTREME:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.EXTREME_MIRROR]
      default:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.EXTREME_MIRROR]
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.selectThemePackForFloor', { floor: floorNumber })}
          </DialogTitle>
        </DialogHeader>

        <Tabs
          value={String(selectedDifficulty)}
          onValueChange={(v) => {
            setSelectedDifficulty(Number(v) as DungeonIdx)
          }}
          className="flex-1 flex flex-col overflow-hidden"
        >
          <TabsList
            className="grid w-full"
            style={{ gridTemplateColumns: `repeat(${availableDifficulties.length}, 1fr)` }}
          >
            {availableDifficulties.map((diff) => (
              <TabsTrigger
                key={diff}
                value={String(diff)}
                style={{
                  color: selectedDifficulty === diff ? getDifficultyColor(diff) : undefined,
                }}
              >
                {getDifficultyLabel(diff)}
              </TabsTrigger>
            ))}
          </TabsList>

          {availableDifficulties.map((diff) => {
            const packs = filterThemePacks(themePackList, floorNumber, diff, usedThemePackIds)

            return (
              <TabsContent
                key={diff}
                value={String(diff)}
                className="mt-4 flex-1 overflow-x-hidden overflow-y-auto"
              >
                {packs.length === 0 ? (
                  <div className="flex items-center justify-center h-32 text-muted-foreground">
                    {t('pages.plannerMD.noThemePacksAvailable')}
                  </div>
                ) : (
                  <ResponsiveCardGrid
                    size={THEME_PACK_GEOMETRY.size}
                    rows="content"
                    mobileScale={CARD_MOBILE_SCALE_DENSE}
                  >
                    {packs.map(({ id, entry }) => {
                      const i18nData = themePackI18n[id]
                      const name = i18nData?.name || `Pack ${id}`

                      return (
                        <div key={id} className="flex flex-col items-center gap-1">
                          <ThemePackViewer
                            packId={id}
                            packEntry={entry}
                            packName={name}
                            onClick={() => {
                              handlePackSelect(id)
                            }}
                            mobileScale={CARD_MOBILE_SCALE_DENSE}
                            enableHoverHighlight
                          />
                          <ThemePackExclusiveGifts giftIds={entry.specificEgoGiftPool} />
                        </div>
                      )
                    })}
                  </ResponsiveCardGrid>
                )}
              </TabsContent>
            )
          })}
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
