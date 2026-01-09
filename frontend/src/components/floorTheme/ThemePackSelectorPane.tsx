import { useState, startTransition } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { DUNGEON_IDX, DIFFICULTY_COLORS, DIFFICULTY_LABELS, type DungeonIdx } from '@/lib/constants'
import { getThemePackImagePath } from '@/lib/assetPaths'
import type { ThemePackList, ThemePackEntry } from '@/types/ThemePackTypes'

interface ThemePackSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number // 1-indexed floor (1-15)
  previousFloorDifficulty: DungeonIdx | null // null for floor 1
  themePackList: ThemePackList
  themePackI18n: Record<string, { name: string; specialName?: string }>
  onSelect: (packId: string, difficulty: DungeonIdx) => void
}

/**
 * Gets available difficulties for a floor based on floor number and previous floor's difficulty
 * Rules from instructions.md:
 * - Show normal when floor is 1F OR previous floor's difficulty is normal
 * - Show hard when floor is 1-10F
 * - Show extreme when floor is 11-15F
 */
function getAvailableDifficulties(
  floorNumber: number,
  previousFloorDifficulty: DungeonIdx | null
): DungeonIdx[] {
  // Floor 11-15: Extreme only
  if (floorNumber >= 11) {
    return [DUNGEON_IDX.EXTREME]
  }

  const available: DungeonIdx[] = []

  // Normal available for 1F OR if previous floor was Normal
  if (floorNumber === 1 || previousFloorDifficulty === DUNGEON_IDX.NORMAL) {
    available.push(DUNGEON_IDX.NORMAL)
  }

  // Hard always available for floors 1-10
  available.push(DUNGEON_IDX.HARD)

  return available
}

/**
 * Filters theme packs that are available for a specific floor and difficulty
 */
function filterThemePacks(
  themePackList: ThemePackList,
  floorNumber: number,
  difficulty: DungeonIdx
): { id: string; entry: ThemePackEntry }[] {
  const result: { id: string; entry: ThemePackEntry }[] = []

  // Convert floor number to selectableFloors index
  // 1 → 0, 2 → 1, 3 → 2, 4 → 3, 5-10 → 4
  const floorIndex = floorNumber <= 4 ? floorNumber - 1 : 4

  for (const [id, entry] of Object.entries(themePackList)) {
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
}: ThemePackSelectorPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  const availableDifficulties = getAvailableDifficulties(floorNumber, previousFloorDifficulty)

  const [selectedDifficulty, setSelectedDifficulty] = useState<DungeonIdx>(
    availableDifficulties[0]
  )

  const filteredPacks = filterThemePacks(themePackList, floorNumber, selectedDifficulty)

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
    <>
      {/* Custom backdrop to block background interaction */}
      {open && (
        <div
          className="fixed inset-0 z-50 bg-black/50 animate-in fade-in-0"
          onClick={() => onOpenChange(false)}
        />
      )}

      <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
        <DialogContent
          className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] overflow-hidden flex flex-col duration-100"
          forceMount
          onPointerDownOutside={(e) => e.preventDefault()}
        >
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
              className="flex-1 overflow-y-auto mt-4"
            >
              {filteredPacks.length === 0 ? (
                <div className="flex items-center justify-center h-32 text-muted-foreground">
                  {t('pages.plannerMD.noThemePacksAvailable')}
                </div>
              ) : (
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-4">
                  {filteredPacks.map(({ id, entry }) => {
                    const name = themePackI18n[id]?.name || `Pack ${id}`
                    const textColor = `#${entry.themePackConfig.textColor}`

                    return (
                      <button
                        key={id}
                        type="button"
                        onClick={() => { handlePackSelect(id); }}
                        aria-label={name}
                        className="selectable relative aspect-[3/4] rounded-lg overflow-hidden cursor-pointer"
                      >
                        <img
                          src={getThemePackImagePath(id)}
                          alt={name}
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute bottom-2 left-0 right-0 px-1 text-center">
                          <span
                            className="text-xs font-bold drop-shadow-[0_1px_1px_rgba(0,0,0,0.8)]"
                            style={{ color: textColor }}
                          >
                            {name}
                          </span>
                        </div>
                      </button>
                    )
                  })}
                </div>
              )}
            </TabsContent>
          ))}
        </Tabs>
      </DialogContent>
    </Dialog>
    </>
  )
}
