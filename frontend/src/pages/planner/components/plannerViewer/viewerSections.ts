/**
 * The ordered sections a mirror-dungeon planner is read in, shared by the guide
 * and tracker viewers so their reveal order, note keys and dialog titles cannot
 * drift apart.
 */

/** Sections that carry a note the reader can open. */
export type NoteSectionId =
  | 'deckBuilder'
  | 'startBuffs'
  | 'startGifts'
  | 'observation'
  | 'skillReplacement'
  | 'comprehensiveGifts'

export interface NoteSection {
  id: NoteSectionId
  /** Key into `MDPlannerContent.sectionNotes`. */
  noteKey: NoteSectionId
  /** i18n key for the section's own heading. */
  titleKey: string
}

/**
 * Note-bearing sections in reveal order. Index is the progressive-reveal slot,
 * which is why the array order is load-bearing.
 */
export const NOTE_SECTIONS: readonly NoteSection[] = [
  { id: 'deckBuilder', noteKey: 'deckBuilder', titleKey: 'pages.plannerMD.deckBuilder' },
  { id: 'startBuffs', noteKey: 'startBuffs', titleKey: 'pages.plannerMD.startBuffs' },
  { id: 'startGifts', noteKey: 'startGifts', titleKey: 'pages.plannerMD.startEgoGift' },
  { id: 'observation', noteKey: 'observation', titleKey: 'pages.plannerMD.egoGiftObservation' },
  {
    id: 'skillReplacement',
    noteKey: 'skillReplacement',
    titleKey: 'pages.plannerMD.skillReplacement.title',
  },
  {
    id: 'comprehensiveGifts',
    noteKey: 'comprehensiveGifts',
    titleKey: 'pages.plannerMD.comprehensiveEgoGiftListView',
  },
] as const
