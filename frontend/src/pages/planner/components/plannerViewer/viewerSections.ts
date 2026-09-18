export type NoteSectionId =
  | 'deckBuilder'
  | 'startBuffs'
  | 'startGifts'
  | 'observation'
  | 'skillReplacement'
  | 'comprehensiveGifts'

export interface NoteSection {
  id: NoteSectionId
  noteKey: NoteSectionId
  titleKey: string
}

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
