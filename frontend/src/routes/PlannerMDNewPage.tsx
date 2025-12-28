import { useState, useMemo, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { MD_CATEGORIES, PLANNER_KEYWORDS, SINNERS, MAX_LEVEL, DEFAULT_SKILL_EA, FLOOR_COUNTS, DUNGEON_IDX } from '@/lib/constants'
import type { MDCategory, DungeonIdx } from '@/lib/constants'
import { getPlannerKeywordIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'
import { DeckBuilder } from '@/components/deckBuilder/DeckBuilder'
import { StartBuffSection } from '@/components/startBuff/StartBuffSection'
import { StartGiftSection } from '@/components/startGift/StartGiftSection'
import { EGOGiftObservationSection } from '@/components/egoGift/EGOGiftObservationSection'
import { EGOGiftComprehensiveListSection } from '@/components/egoGift/EGOGiftComprehensiveListSection'
import { SkillReplacementSection } from '@/components/skillReplacement/SkillReplacementSection'
import { FloorThemeGiftSection } from '@/components/floorTheme/FloorThemeGiftSection'
import type { SinnerEquipment, SkillEAState } from '@/types/DeckTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'

/**
 * Calculates byte length of a UTF-8 string
 */
function getByteLength(str: string): number {
  return new TextEncoder().encode(str).length
}

const MAX_TITLE_BYTES = 256

interface KeywordSelectorProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  placeholder: string
  clearLabel: string
}

function KeywordSelector({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  placeholder,
  clearLabel,
}: KeywordSelectorProps) {
  const [isOpen, setIsOpen] = useState(false)

  const toggleOption = (option: string) => {
    const newSelection = new Set(selectedOptions)
    if (newSelection.has(option)) {
      newSelection.delete(option)
    } else {
      newSelection.add(option)
    }
    onSelectionChange(newSelection)
  }

  const clearAll = () => {
    onSelectionChange(new Set())
  }

  return (
    <div className="space-y-2">
      {/* Selected Keywords Display */}
      <div
        onClick={() => setIsOpen(!isOpen)}
        className="min-h-14 p-2 border border-border rounded-md bg-card cursor-pointer hover:border-primary/50 transition-colors"
      >
        {selectedOptions.size === 0 ? (
          <span className="text-muted-foreground text-sm">{placeholder}</span>
        ) : (
          <div className="flex flex-wrap gap-2">
            {Array.from(selectedOptions).map((option) => (
              <div
                key={option}
                className="w-8 h-8 rounded-md border-2 border-primary bg-primary/10"
                title={getKeywordDisplayName(option)}
              >
                <img
                  src={getIconPath(option)}
                  alt={getKeywordDisplayName(option)}
                  className="w-full h-full object-contain"
                />
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Selector Panel */}
      {isOpen && (
        <div className="bg-card border border-border rounded-md p-3 space-y-3">
          {/* Clear Button */}
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">
              {selectedOptions.size} selected
            </span>
            <Button variant="ghost" size="sm" onClick={clearAll}>
              {clearLabel}
            </Button>
          </div>

          {/* Options Grid */}
          <div className="flex flex-wrap gap-2">
            {options.map((option) => {
              const isSelected = selectedOptions.has(option)
              const label = getKeywordDisplayName(option)
              return (
                <button
                  key={option}
                  onClick={() => toggleOption(option)}
                  className={`shrink-0 w-10 h-10 rounded-md border-2 transition-all ${
                    isSelected
                      ? 'border-primary bg-primary/10'
                      : 'border-border bg-button hover:border-primary/50'
                  }`}
                  title={label}
                >
                  <img
                    src={getIconPath(option)}
                    alt={label}
                    className="w-full h-full object-contain"
                  />
                </button>
              )
            })}
          </div>

          {/* Close Button */}
          <div className="flex justify-end">
            <Button variant="outline" size="sm" onClick={() => setIsOpen(false)}>
              Done
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Creates default equipment for all sinners
 * Note: skillEA is managed separately, not in equipment
 */
function createDefaultEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}
  SINNERS.forEach((sinner, index) => {
    const sinnerIdPart = (index + 1).toString().padStart(2, '0')
    const defaultIdentityId = `1${sinnerIdPart}01`
    const defaultEgoId = `2${sinnerIdPart}01`
    equipment[sinner] = {
      identity: { id: defaultIdentityId, uptie: 4, level: MAX_LEVEL },
      egos: {
        ZAYIN: { id: defaultEgoId, threadspin: 4 },
      },
    }
  })
  return equipment
}

/**
 * Creates default skill EA state for all sinners
 */
function createDefaultSkillEAState(): Record<string, SkillEAState> {
  const state: Record<string, SkillEAState> = {}
  for (const sinner of SINNERS) {
    state[sinner] = { ...DEFAULT_SKILL_EA }
  }
  return state
}

export default function PlannerMDNewPage() {
  const { t } = useTranslation()

  // State for category selector (default: 5F)
  const [category, setCategory] = useState<MDCategory>('5F')

  // State for keyword multi-selector (default: empty)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())

  // State for start buff selection
  const [selectedBuffIds, setSelectedBuffIds] = useState<Set<number>>(new Set())

  // State for start gift selection
  const [selectedGiftKeyword, setSelectedGiftKeyword] = useState<string | null>(null)
  const [selectedGiftIds, setSelectedGiftIds] = useState<Set<string>>(new Set())

  // State for observation gift selection
  const [observationGiftIds, setObservationGiftIds] = useState<Set<string>>(new Set())

  // State for comprehensive gift selection (encoded: enhancement + giftId)
  const [comprehensiveGiftIds, setComprehensiveGiftIds] = useState<Set<string>>(new Set())

  // State for deck equipment (lifted for saving)
  const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>(createDefaultEquipment)
  const [deploymentOrder, setDeploymentOrder] = useState<number[]>([])

  // State for skill EA (independent from equipment's skillEA for section control)
  const [skillEAState, setSkillEAState] = useState<Record<string, SkillEAState>>(createDefaultSkillEAState)

  // State for title input
  const [title, setTitle] = useState<string>('')

  // State for floor theme selections (per-floor: themePackId, difficulty, giftIds)
  const [floorSelections, setFloorSelections] = useState<FloorThemeSelection[]>(() =>
    Array.from({ length: 15 }, () => ({
      themePackId: null,
      difficulty: DUNGEON_IDX.NORMAL,
      giftIds: new Set<string>(),
    }))
  )

  // Get floor count based on category
  const floorCount = FLOOR_COUNTS[category]

  const titleByteLength = getByteLength(title)
  const isTitleValid = titleByteLength <= MAX_TITLE_BYTES

  // Handler for theme pack selection on a floor
  const handleFloorThemePackSelect = (floorIndex: number, packId: string, difficulty: DungeonIdx) => {
    setFloorSelections((prev) => {
      const next = [...prev]
      next[floorIndex] = {
        ...next[floorIndex],
        themePackId: packId,
        difficulty,
        // Clear gift selection when theme pack changes
        giftIds: new Set<string>(),
      }
      return next
    })
  }

  // Handler for gift selection on a floor
  const handleFloorGiftSelectionChange = (floorIndex: number, giftIds: Set<string>) => {
    setFloorSelections((prev) => {
      const next = [...prev]
      next[floorIndex] = {
        ...next[floorIndex],
        giftIds,
      }
      return next
    })
  }

  // Get previous floor's difficulty for constraint checking
  const getPreviousFloorDifficulty = (floorIndex: number): DungeonIdx | null => {
    if (floorIndex === 0) return null
    return floorSelections[floorIndex - 1].difficulty
  }

  // Generate floor indices array for rendering
  const floorIndices = useMemo(() =>
    Array.from({ length: floorCount }, (_, i) => i),
    [floorCount]
  )

  const handleTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setTitle(e.target.value)
  }

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">{t('pages.plannerMD.title')}</h1>
      <p className="text-muted-foreground mb-6">{t('pages.plannerMD.description')}</p>

      <div className="bg-background rounded-lg p-6 space-y-6">
        {/* Category Selector */}
        <div className="space-y-2">
          <label className="text-sm font-medium">{t('pages.plannerMD.category')}</label>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" className="w-24 justify-between">
                {category}
                <ChevronDown className="ml-2 h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              {MD_CATEGORIES.map((cat) => (
                <DropdownMenuItem key={cat} onClick={() => setCategory(cat)}>
                  {cat}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {/* Keyword Multi-Selector */}
        <div className="space-y-2">
          <label className="text-sm font-medium">{t('pages.plannerMD.keywords')}</label>
          <KeywordSelector
            options={PLANNER_KEYWORDS}
            selectedOptions={selectedKeywords}
            onSelectionChange={setSelectedKeywords}
            getIconPath={getPlannerKeywordIconPath}
            placeholder={t('pages.plannerMD.keywordsPlaceholder')}
            clearLabel={t('pages.plannerMD.clearKeywords')}
          />
        </div>

        {/* Title Input */}
        <div className="space-y-2">
          <label className="text-sm font-medium">{t('pages.plannerMD.planTitle')}</label>
          <div className="flex flex-col gap-1">
            <input
              type="text"
              value={title}
              onChange={handleTitleChange}
              placeholder={t('pages.plannerMD.titlePlaceholder')}
              className={`w-full max-w-md px-3 py-2 border rounded-md bg-background ${
                !isTitleValid ? 'border-destructive' : 'border-border'
              } focus:outline-none focus:ring-2 focus:ring-primary`}
            />
            <span
              className={`text-xs ${
                !isTitleValid ? 'text-destructive' : 'text-muted-foreground'
              }`}
            >
              {titleByteLength}/{MAX_TITLE_BYTES} {t('pages.plannerMD.bytes')}
            </span>
          </div>
        </div>

        {/* Deck Builder */}
        <DeckBuilder
          equipment={equipment}
          setEquipment={setEquipment}
          deploymentOrder={deploymentOrder}
          setDeploymentOrder={setDeploymentOrder}
        />

        {/* Start Buff Section */}
        <StartBuffSection
          mdVersion={6}
          selectedBuffIds={selectedBuffIds}
          onSelectionChange={setSelectedBuffIds}
        />

        {/* Start Gift Section */}
        <StartGiftSection
          mdVersion={6}
          selectedBuffIds={selectedBuffIds}
          selectedKeyword={selectedGiftKeyword}
          selectedGiftIds={selectedGiftIds}
          onKeywordChange={setSelectedGiftKeyword}
          onGiftSelectionChange={setSelectedGiftIds}
        />

        {/* EGO Gift Observation Section */}
        <Suspense
          fallback={
            <div className="bg-muted border border-border rounded-md p-6">
              <div className="text-center text-gray-500 py-8">
                Loading observation data...
              </div>
            </div>
          }
        >
          <EGOGiftObservationSection
            selectedGiftIds={observationGiftIds}
            onGiftSelectionChange={setObservationGiftIds}
          />
        </Suspense>

        {/* Skill Replacement Section */}
        <Suspense
          fallback={
            <div className="bg-muted border border-border rounded-md p-6">
              <div className="text-center text-gray-500 py-8">
                Loading skill data...
              </div>
            </div>
          }
        >
          <SkillReplacementSection
            equipment={equipment}
            skillEAState={skillEAState}
            setSkillEAState={setSkillEAState}
          />
        </Suspense>

        {/* EGO Gift Comprehensive List Section */}
        <Suspense
          fallback={
            <div className="bg-muted border border-border rounded-md p-6">
              <div className="text-center text-gray-500 py-8">
                Loading gift data...
              </div>
            </div>
          }
        >
          <EGOGiftComprehensiveListSection
            selectedGiftIds={comprehensiveGiftIds}
            onGiftSelectionChange={setComprehensiveGiftIds}
          />
        </Suspense>

        {/* Floor Theme and Gift Sections */}
        <div className="space-y-4">
          <h2 className="text-xl font-semibold">{t('pages.plannerMD.floorThemes')}</h2>
          <Suspense
            fallback={
              <div className="bg-muted border border-border rounded-md p-6">
                <div className="text-center text-gray-500 py-8">
                  Loading theme pack data...
                </div>
              </div>
            }
          >
            <div className="space-y-2">
              {floorIndices.map((floorIndex) => {
                const floorNumber = floorIndex + 1
                const selection = floorSelections[floorIndex]
                return (
                  <FloorThemeGiftSection
                    key={floorIndex}
                    floorNumber={floorNumber}
                    previousFloorDifficulty={getPreviousFloorDifficulty(floorIndex)}
                    selectedThemePackId={selection.themePackId}
                    selectedDifficulty={selection.difficulty}
                    selectedGiftIds={selection.giftIds}
                    onThemePackSelect={(packId, difficulty) =>
                      handleFloorThemePackSelect(floorIndex, packId, difficulty)
                    }
                    setSelectedGiftIds={(giftIds) =>
                      handleFloorGiftSelectionChange(floorIndex, giftIds)
                    }
                  />
                )
              })}
            </div>
          </Suspense>
        </div>
      </div>
    </div>
  )
}
