import { useState, useCallback, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { MD_CATEGORIES, PLANNER_KEYWORDS } from '@/lib/constants'
import type { MDCategory } from '@/lib/constants'
import { getPlannerKeywordIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'
import { DeckBuilder } from '@/components/deckBuilder/DeckBuilder'
import { StartBuffSection } from '@/components/startBuff/StartBuffSection'
import { StartGiftSection } from '@/components/startGift/StartGiftSection'
import { EGOGiftObservationSection } from '@/components/egoGift/EGOGiftObservationSection'
import { EGOGiftComprehensiveListSection } from '@/components/egoGift/EGOGiftComprehensiveListSection'

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

  // State for title input
  const [title, setTitle] = useState<string>('')

  const titleByteLength = getByteLength(title)
  const isTitleValid = titleByteLength <= MAX_TITLE_BYTES

  const handleTitleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setTitle(e.target.value)
  }, [])

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
        <DeckBuilder />

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
      </div>
    </div>
  )
}
