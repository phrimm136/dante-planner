import { Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { DeckBuilderSummary } from '@/components/deckBuilder/DeckBuilderSummary'
import { StartBuffSection } from '@/components/startBuff/StartBuffSection'
import { StartGiftSummary } from '@/components/startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '@/components/egoGift/EGOGiftObservationSummary'
import { SkillReplacementSection } from '@/components/skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { FloorGalleryTracker } from './FloorGalleryTracker'
import { PlannerSection } from '@/components/common/PlannerSection'
import { NoteEditor } from '@/components/noteEditor/NoteEditor'
import { Skeleton } from '@/components/ui/skeleton'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'
import { FLOOR_COUNTS } from '@/lib/constants'
import type { MDCategory } from '@/lib/constants'
import { deserializeSets } from '@/schemas/PlannerSchemas'

interface GuideModeViewerProps {
  planner: SaveablePlanner
}

/**
 * Read-only viewer for planner in guide mode.
 * Displays all sections from editor without editing capabilities.
 * Section order matches PlannerMDNewPage exactly.
 */
export function GuideModeViewer({ planner }: GuideModeViewerProps) {
  const { t } = useTranslation(['planner', 'common'])

  const content = planner.content as MDPlannerContent
  const category = planner.config.type === 'MIRROR_DUNGEON' ? planner.config.category : '5F'
  const floorCount = FLOOR_COUNTS[category as MDCategory]

  const deserialized = useMemo(
    () =>
      deserializeSets({
        selectedKeywords: content.selectedKeywords,
        selectedBuffIds: content.selectedBuffIds,
        selectedGiftIds: content.selectedGiftIds,
        observationGiftIds: content.observationGiftIds,
        comprehensiveGiftIds: content.comprehensiveGiftIds,
        floorSelections: content.floorSelections,
      }),
    [content]
  )

  return (
    <div className="bg-background rounded-lg p-6 space-y-2">
      {/* Deck Builder Section */}
      <Suspense
        fallback={
          <div className="space-y-2">
            <div className="border-2 border-border rounded-lg p-4">
              <div className="flex flex-wrap gap-2">
                {Array.from({ length: 12 }).map((_, i) => (
                  <Skeleton key={i} className="w-16 h-20 rounded-md" style={{ animationDelay: `${i * 40}ms` }} />
                ))}
              </div>
            </div>
          </div>
        }
      >
        <DeckBuilderSummary
          equipment={content.equipment}
          deploymentOrder={content.deploymentOrder}
          onToggleDeploy={() => {}}
          onImport={() => {}}
          onExport={() => {}}
          onResetOrder={() => {}}
          onEditDeck={() => {}}
          readOnly={true}
        />
      </Suspense>
      <NoteEditor
        value={content.sectionNotes.deckBuilder}
        onChange={() => {}}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />

      {/* Start Buff Section */}
      <Suspense
        fallback={
          <div className="space-y-2">
            <Skeleton className="h-32 w-full rounded-lg" />
          </div>
        }
      >
        <StartBuffSection
          mdVersion={planner.metadata.contentVersion}
          selectedBuffIds={deserialized.selectedBuffIds}
          onSelectionChange={() => {}}
          onClick={() => {}}
          readOnly={true}
        />
      </Suspense>
      <NoteEditor
        value={content.sectionNotes.startBuffs}
        onChange={() => {}}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />

      {/* Start Gift Section */}
      <Suspense
        fallback={
          <div className="space-y-2">
            <Skeleton className="h-32 w-full rounded-lg" />
          </div>
        }
      >
        <StartGiftSummary
          selectedKeyword={content.selectedGiftKeyword}
          selectedGiftIds={deserialized.selectedGiftIds}
          onClick={() => {}}
          readOnly={true}
        />
      </Suspense>
      <NoteEditor
        value={content.sectionNotes.startGifts}
        onChange={() => {}}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />

      {/* EGO Gift Observation Section */}
      <Suspense
        fallback={
          <PlannerSection title={t('pages.plannerMD.egoGiftObservation')}>
            <div className="space-y-4">
              <div className="flex justify-end">
                <div className="flex items-center gap-1">
                  <Skeleton className="w-8 h-8 rounded-md" />
                  <Skeleton className="w-12 h-6" />
                </div>
              </div>
              <div className="flex flex-wrap gap-2 p-2 min-h-28">
                {Array.from({ length: 6 }).map((_, i) => (
                  <Skeleton key={i} className="w-24 h-24 rounded-md" style={{ animationDelay: `${i * 80}ms` }} />
                ))}
              </div>
            </div>
          </PlannerSection>
        }
      >
        <EGOGiftObservationSummary selectedGiftIds={deserialized.observationGiftIds} onClick={() => {}} readOnly={true} />
      </Suspense>
      <NoteEditor
        value={content.sectionNotes.observation}
        onChange={() => {}}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />

      {/* Skill Replacement Section */}
      <Suspense
        fallback={
          <PlannerSection title={t('pages.plannerMD.skillReplacement.title')}>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
              {Array.from({ length: 12 }).map((_, i) => (
                <div
                  key={i}
                  className="flex flex-col items-center gap-1 p-2 rounded-lg border-2 border-border bg-card"
                  style={{ animationDelay: `${i * 60}ms` }}
                >
                  <Skeleton className="w-24 h-24 rounded-md" />
                  <div className="flex gap-1">
                    <Skeleton className="w-7 h-7 rounded-sm" />
                    <Skeleton className="w-7 h-7 rounded-sm" />
                    <Skeleton className="w-7 h-7 rounded-sm" />
                  </div>
                </div>
              ))}
            </div>
          </PlannerSection>
        }
      >
        <SkillReplacementSection
          equipment={content.equipment}
          plannedEAState={content.skillEAState}
          setSkillEAState={() => {}}
          readOnly={true}
        />
      </Suspense>
      <NoteEditor
        value={content.sectionNotes.skillReplacement}
        onChange={() => {}}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />

      {/* Comprehensive Gift Grid (from all floors) */}
      <PlannerSection title={t('pages.plannerMD.comprehensiveEgoGiftList')}>
        <Suspense
          fallback={
            <div className="text-center text-gray-500 py-8">{t('pages.plannerMD.loading.EGOGiftData')}</div>
          }
        >
          <ComprehensiveGiftGridTracker
            floorSelections={deserialized.floorSelections}
            doneMarks={{}}
            hoveredThemePackId={null}
          />
        </Suspense>
      </PlannerSection>
      <NoteEditor
        value={content.sectionNotes.comprehensiveGifts}
        onChange={() => {}}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />

      {/* Floor Theme Sections */}
      <Suspense
        fallback={
          <div className="text-center text-gray-500 py-8">{t('pages.plannerMD.loading.themePackData')}</div>
        }
      >
        <FloorGalleryTracker
          floorSelections={deserialized.floorSelections}
          sectionNotes={content.sectionNotes}
          floorCount={floorCount}
        />
      </Suspense>
    </div>
  )
}
