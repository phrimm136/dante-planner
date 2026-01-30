import { useState, startTransition } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { DUNGEON_IDX, DIFFICULTY_COLORS, DIFFICULTY_LABELS, CARD_GRID, type DungeonIdx, type MDCategory } from '@/lib/constants'
import { ThemePackViewer } from './ThemePackViewer'
import type { ThemePackList, ThemePackEntry } from '@/types/ThemePackTypes'

interface ThemePackSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number // 1-indexed floor (1-15)
  previousFloorDifficulty: DungeonIdx | null // null for floor 1
  themePackList: ThemePackList
  themePackI18n: Record<string, { name: string; specialName?: string }>
  onSelect: (packId: string, difficulty: DungeonIdx) => void
  /** Set of theme pack IDs already used on other floors (to prevent duplicates) */
  usedThemePackIds: Set<string>
  /** MD category for difficulty restriction (10F/15F restrict first floor to Hard only) */
  category: MDCategory
}

/**
 * Gets available difficulties for a floor based on floor number, previous floor's difficulty, and category
 * Rules:
 * - Floor 11-15: Extreme only
 * - Floor 1 with 10F/15F category: Hard only (no Normal option)
 * - Floor 1 with 5F category: Normal and Hard available
 * - Floor 2-10: Normal available only if previous floor was Normal, Hard always available
 */
function getAvailableDifficulties(
  floorNumber: number,
  previousFloorDifficulty: DungeonIdx | null,
  category: MDCategory
): DungeonIdx[] {
  // Floor 11-15: Extreme only
  if (floorNumber >= 11) {
    return [DUNGEON_IDX.EXTREME]
  }

  const available: DungeonIdx[] = []

  // Normal available for:
  // - 1F with 5F category (N/H mode allows Normal start)
  // - Any floor if previous floor was Normal
  const isFirstFloorWithNormalAllowed = floorNumber === 1 && category === '5F'
  if (isFirstFloorWithNormalAllowed || previousFloorDifficulty === DUNGEON_IDX.NORMAL) {
    available.push(DUNGEON_IDX.NORMAL)
  }

  // Hard always available for floors 1-10
  available.push(DUNGEON_IDX.HARD)

  return available
}

/**
 * Filters theme packs that are available for a specific floor and difficulty
 * Excludes theme packs that are already used on other floors
 */
function filterThemePacks(
  themePackList: ThemePackList,
  floorNumber: number,
  difficulty: DungeonIdx,
  usedThemePackIds: Set<string>
): { id: string; entry: ThemePackEntry }[] {
  const result: { id: string; entry: ThemePackEntry }[] = []

  // Convert floor number to selectableFloors index
  // 1 → 0, 2 → 1, 3 → 2, 4 → 3, 5-10 → 4
  const floorIndex = floorNumber <= 4 ? floorNumber - 1 : 4

  for (const [id, entry] of Object.entries(themePackList)) {
    // Skip if this theme pack is already used on another floor
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
        // For normal/hard, check if floor is in selectableFloors
        if (condition.selectableFloors?.includes(floorIndex)) {
          result.push({ id, entry })
          break
        }
      }
    }
  }

  return result
}

/**
 * Dialog for selecting a theme pack with difficulty tabs
 */
export function ThemePackSelectorPane({
  open,
  onOpenChange,
  floorNumber,
  previousFloorDifficulty,
  themePackList,
  themePackI18n,
  onSelect,
  usedThemePackIds,
  category,
}: ThemePackSelectorPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  const availableDifficulties = getAvailableDifficulties(floorNumber, previousFloorDifficulty, category)

  const [selectedDifficulty, setSelectedDifficulty] = useState<DungeonIdx>(
    availableDifficulties[0]
  )

  const filteredPacks = filterThemePacks(themePackList, floorNumber, selectedDifficulty, usedThemePackIds)

  const handlePackSelect = (packId: string) => {
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

  const getDifficultyColor = (idx: DungeonIdx): string => {
    switch (idx) {
      case DUNGEON_IDX.NORMAL:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.NORMAL]
      case DUNGEON_IDX.HARD:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.HARD]
      case DUNGEON_IDX.EXTREME:
        return DIFFICULTY_COLORS[DIFFICULTY_LABELS.EXTREME_MIRROR]
      default:
        // Fallback to extreme mirror color (white) for unknown difficulty
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
          onValueChange={(v) => { setSelectedDifficulty(Number(v) as DungeonIdx); }}
          className="flex-1 flex flex-col overflow-hidden"
        >
          <TabsList className="grid w-full" style={{ gridTemplateColumns: `repeat(${availableDifficulties.length}, 1fr)` }}>
            {availableDifficulties.map((diff) => (
              <TabsTrigger
                key={diff}
                value={String(diff)}
                style={{ color: selectedDifficulty === diff ? getDifficultyColor(diff) : undefined }}
              >
                {getDifficultyLabel(diff)}
              </TabsTrigger>
            ))}
          </TabsList>

          {availableDifficulties.map((diff) => (
            <TabsContent
              key={diff}
              value={String(diff)}
              className="mt-4 flex-1 overflow-x-hidden overflow-y-auto"
            >
              {filteredPacks.length === 0 ? (
                <div className="flex items-center justify-center h-32 text-muted-foreground">
                  {t('pages.plannerMD.noThemePacksAvailable')}
                </div>
              ) : (
                <ResponsiveCardGrid
                  cardWidth={CARD_GRID.WIDTH.THEME_PACK}
                  cardHeight={CARD_GRID.HEIGHT.THEME_PACK}
                  mobileScale={0.6}
                >
                  {filteredPacks.map(({ id, entry }) => {
                    const i18nData = themePackI18n[id]
                    const name = i18nData?.name || `Pack ${id}`

                    return (
                      <ScaledCardWrapper
                        key={id}
                        cardWidth={CARD_GRID.WIDTH.THEME_PACK}
                        cardHeight={CARD_GRID.HEIGHT.THEME_PACK}
                        mobileScale={0.6}
                      >
                        <ThemePackViewer
                          packId={id}
                          packEntry={entry}
                          packName={name}
                          specialName={i18nData?.specialName}
                          onClick={() => { handlePackSelect(id); }}
                          enableHoverHighlight
                        />
                      </ScaledCardWrapper>
                    )
                  })}
                </ResponsiveCardGrid>
              )}
            </TabsContent>
          ))}
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
