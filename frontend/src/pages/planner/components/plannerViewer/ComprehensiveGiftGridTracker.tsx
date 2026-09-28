import { memo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { decodeGiftSelections } from '@/pages/egoGift'
import { sortEGOGifts } from '@/pages/egoGift'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
import { cn } from '@/lib/utils'
import type { EGOGiftEntity } from '@/pages/egoGift'
import type { EncodedGiftId, EnhancementLevel } from '@/shared/gameData'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { useSearchMappings } from '@/shared/filter'
import { CardSlot, EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftTooltip } from '@/pages/egoGift'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import type { SerializableFloorSelection } from '../../types/PlannerTypes'

interface ComprehensiveGiftGridTrackerProps {
  floorSelections: SerializableFloorSelection[]
  floorCount: number
  hoveredThemePackId: string | null
  egoGiftDoneMarks?: Set<string>
  onToggleEgoGiftDone?: (encodedId: string) => void
  readOnly?: boolean
  comprehensiveGiftIds: EncodedGiftId[]
  height?: number | undefined
}

interface DecodedGift {
  item: EGOGiftEntity
  enhancement: EnhancementLevel
  encodedId: string
}

export function ComprehensiveGiftGridTracker({
  floorSelections,
  floorCount,
  hoveredThemePackId,
  egoGiftDoneMarks,
  onToggleEgoGiftDone,
  readOnly,
  comprehensiveGiftIds,
  height,
}: ComprehensiveGiftGridTrackerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()
  const { keywordToValue } = useSearchMappings()

  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  const mobileScale = CARD_MOBILE_SCALE

  const floors = floorSelections.slice(0, floorCount)

  const allComprehensiveGiftIds = (() => {
    const allGifts = new Set(comprehensiveGiftIds)
    floors.forEach((selection) => {
      selection.giftIds?.forEach((giftId) => allGifts.add(giftId))
    })
    return allGifts
  })()

  const highlightedGiftIds = (() => {
    const ids = new Set<string>()
    if (hoveredThemePackId) {
      floors.forEach((selection) => {
        if (selection.themePackId === hoveredThemePackId) {
          selection.giftIds?.forEach((giftId) => ids.add(giftId))
        }
      })
    }
    return ids
  })()

  const selectedGifts = (() => {
    const highlighted: DecodedGift[] = []
    const regular: DecodedGift[] = []
    const done: DecodedGift[] = []

    for (const gift of decodeGiftSelections(allComprehensiveGiftIds, spec, i18n)) {
      const { encodedId, item } = gift
      const giftKeyword = item.keyword ?? 'None'

      if (selectedKeywords.size > 0 && !selectedKeywords.has(giftKeyword)) {
        continue
      }

      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = item.name.toLowerCase().includes(lowerQuery)
        const keywordMatch = Array.from(keywordToValue.entries()).some(
          ([naturalLang, pascalValues]) => {
            if (naturalLang.includes(lowerQuery)) {
              return pascalValues.includes(giftKeyword)
            }
            return false
          },
        )
        if (!nameMatch && !keywordMatch) continue
      }

      const isHighlighted = highlightedGiftIds.has(encodedId)
      const isDone = egoGiftDoneMarks?.has(encodedId) ?? false

      if (isHighlighted) {
        highlighted.push(gift)
      } else if (isDone) {
        done.push(gift)
      } else {
        regular.push(gift)
      }
    }

    const sortGroup = (gifts: DecodedGift[]) => {
      const itemToGift = new Map(gifts.map((g) => [g.item, g]))
      return sortEGOGifts(
        gifts.map((g) => g.item),
        sortMode,
      ).map((item) => itemToGift.get(item)!)
    }

    return [...sortGroup(highlighted), ...sortGroup(regular), ...sortGroup(done)]
  })()

  const stretch = height === undefined

  const hasAnyGifts = allComprehensiveGiftIds.size > 0
  const hasFilteredGifts = selectedGifts.length > 0
  const hasActiveFilters = selectedKeywords.size > 0 || searchQuery.length > 0

  if (!hasAnyGifts) {
    return (
      <div className={cn('flex', stretch && 'flex-1 min-h-0')} style={{ height }}>
        <EmptyStatePlaceholder
          label={t('pages.plannerMD.emptyState.noEgoGifts')}
          className="flex-1"
        />
      </div>
    )
  }

  return (
    <div className={cn('flex flex-col gap-2', stretch && 'flex-1 min-h-0')}>
      <EGOGiftFilterBar
        selectedKeywords={selectedKeywords}
        onKeywordsChange={setSelectedKeywords}
        sortMode={sortMode}
        onSortModeChange={setSortMode}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
      />

      {hasFilteredGifts ? (
        <ScrollArea className={cn(stretch && 'flex-1 min-h-0')} style={{ height }}>
          <div className="flex flex-wrap gap-2 p-2 min-h-24">
            {selectedGifts.map(({ item, enhancement, encodedId }) => {
              const isHighlighted = highlightedGiftIds.has(encodedId)
              const isDone = egoGiftDoneMarks?.has(encodedId) ?? false

              return (
                <EgoGiftCardWithOverlay
                  key={encodedId}
                  item={item}
                  enhancement={enhancement}
                  encodedId={encodedId}
                  isHighlighted={isHighlighted}
                  isDone={isDone}
                  mobileScale={mobileScale}
                  readOnly={readOnly}
                  onToggleDone={onToggleEgoGiftDone}
                />
              )
            })}
          </div>
        </ScrollArea>
      ) : (
        <div className={cn('flex', stretch && 'flex-1 min-h-0')} style={{ height }}>
          <EmptyStatePlaceholder
            label={
              hasActiveFilters
                ? t('pages.plannerMD.emptyState.noFilterResults')
                : t('pages.plannerMD.emptyState.noEgoGifts')
            }
            className="flex-1"
          />
        </div>
      )}
    </div>
  )
}

interface EgoGiftCardWithOverlayProps {
  item: EGOGiftEntity
  enhancement: EnhancementLevel
  encodedId: string
  isHighlighted: boolean
  isDone: boolean
  mobileScale: number
  readOnly?: boolean | undefined
  onToggleDone?: ((encodedId: string) => void) | undefined
}

/**
 * `decodeGiftSelections` mints a fresh `item` per render, so the default
 * comparison never bails out. `id` and `name` are the only fields the card
 * renders that vary — `name` carries the active language.
 */
const EgoGiftCardWithOverlay = memo(
  EgoGiftCardWithOverlayImpl,
  (prev, next) =>
    prev.item.id === next.item.id &&
    prev.item.name === next.item.name &&
    prev.enhancement === next.enhancement &&
    prev.encodedId === next.encodedId &&
    prev.isHighlighted === next.isHighlighted &&
    prev.isDone === next.isDone &&
    prev.mobileScale === next.mobileScale &&
    prev.readOnly === next.readOnly &&
    prev.onToggleDone === next.onToggleDone,
)

function EgoGiftCardWithOverlayImpl({
  item,
  enhancement,
  encodedId,
  isHighlighted,
  isDone,
  mobileScale,
  readOnly,
  onToggleDone,
}: EgoGiftCardWithOverlayProps) {
  const { t } = useTranslation(['common'])
  const [isHovered, setIsHovered] = useState(false)

  return (
    <CardSlot size={EGO_GIFT_GEOMETRY.size} mobileScale={mobileScale}>
      <EGOGiftTooltip giftId={item.id} enhancement={enhancement}>
        <div
          className="relative w-full"
          onMouseEnter={() => setIsHovered(true)}
          onMouseLeave={() => setIsHovered(false)}
        >
          <div className={cn(isDone && 'brightness-50')}>
            <EGOGiftCard gift={item} enhancement={enhancement} isSelected={isHighlighted} />
          </div>
          {!readOnly && isHovered && (
            <div className="absolute inset-0 flex items-center justify-center">
              <Button
                size="icon"
                variant={isDone ? 'default' : 'secondary'}
                className="h-8 w-8"
                onClick={(e) => {
                  e.stopPropagation()
                  onToggleDone?.(encodedId)
                }}
                aria-label={
                  isDone
                    ? t('common:markAsNotDone', 'Mark as Not Done')
                    : t('common:markAsDone', 'Mark as Done')
                }
              >
                <CheckCircle2 className="h-4 w-4" />
              </Button>
            </div>
          )}
        </div>
      </EGOGiftTooltip>
    </CardSlot>
  )
}
