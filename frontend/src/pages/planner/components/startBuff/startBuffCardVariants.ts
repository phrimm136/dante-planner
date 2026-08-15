import { MD_ACCENT_COLORS } from '@/lib/constants'

/** Border-image geometry of one enhancement button state */
export interface EnhancementBorder {
  /** Enhancement level whose background frame is drawn */
  bgLevel: 0 | 1 | 2
  width: number
  slice: number
  outset: number
}

export interface StartBuffCardVariant {
  root: string
  pane: string
  costAnchor: string
  costText: string
  costTextShadow?: string
  nameRowHeight: string
  buffIcon: string
  nameWrapper: string
  nameMaxFontSize: number
  nameTextShadow?: string
  description: string
  descriptionColor?: string
  enhancementRow: string
  enhancementSlot: string
  enhancementStates: {
    unselected: EnhancementBorder
    plus1: EnhancementBorder
    plus2: EnhancementBorder
  }
  /** Extra frame drawn over a selected enhancement button */
  enhancementOverlay?: { className: string; width: number; slice: number; outset: number }
  highlight: string
}

/** Mirror Dungeon versions with their own card artwork and layout */
export type StartBuffCardVersion = 6 | 7

export const CARD_VARIANTS: Record<StartBuffCardVersion, StartBuffCardVariant> = {
  6: {
    root: 'w-68 h-80',
    pane: 'w-full h-full object-cover',
    costAnchor: 'absolute left-21/32 top-5/8 -translate-y-1/2 flex items-center gap-1',
    costText: 'text-[25px] -translate-y-1',
    nameRowHeight: '12%',
    buffIcon: 'w-14 h-14 ml-8 object-contain',
    nameWrapper: 'ml-1',
    nameMaxFontSize: 20,
    description: 'flex-1 overflow-y-auto px-3 py-2 m-3.5 scrollbar-hide',
    enhancementRow: 'flex gap-2 px-7 pb-8',
    enhancementSlot: 'flex-1 h-6 relative overflow-visible',
    enhancementStates: {
      unselected: { bgLevel: 0, width: 10, slice: 20, outset: 0 },
      plus1: { bgLevel: 1, width: 13, slice: 28, outset: 2 },
      plus2: { bgLevel: 2, width: 13, slice: 32, outset: 2 },
    },
    highlight:
      'absolute inset-0 w-66 h-77.5 justify-center translate-x-0.25 translate-y-1.75 pointer-events-none transition-opacity duration-200',
  },
  7: {
    root: '',
    pane: 'w-66 h-80 object-fill',
    costAnchor:
      'absolute left-3/4 top-9/32 -translate-x-3 -translate-y-1/2 flex items-center gap-1',
    costText: 'text-[30px] -translate-y-[3px] ',
    costTextShadow: '1px 1px 1px black',
    nameRowHeight: '5%',
    buffIcon: 'w-16 h-16 ml-6 object-contain',
    nameWrapper: 'translate-y-1/8',
    nameMaxFontSize: 22,
    nameTextShadow: '2px 2px 1px black',
    description: 'flex-1 overflow-y-auto px-2 py-2 m-3 scrollbar-hide mt-10 mr-5',
    descriptionColor: MD_ACCENT_COLORS[7],
    enhancementRow: 'flex px-5.25 pb-5',
    enhancementSlot: 'flex-1 h-7 relative overflow-visible mx-1.25 -translate-x-1',
    enhancementStates: {
      unselected: { bgLevel: 0, width: 6, slice: 8, outset: 0 },
      plus1: { bgLevel: 0, width: 6, slice: 8, outset: 0 },
      plus2: { bgLevel: 0, width: 6, slice: 8, outset: 0 },
    },
    enhancementOverlay: {
      className:
        'absolute inset-0 pointer-events-none overflow-visible -translate-x-[1.5px] -translate-y-[1px]',
      width: 8,
      slice: 16,
      outset: 2.5,
    },
    highlight:
      'absolute inset-0 w-67.5 h-81 justify-center -translate-x-0.5 -translate-y-0.25 pointer-events-none transition-opacity duration-200',
  },
}

const FALLBACK_MD_VERSION: StartBuffCardVersion = 6

function hasCardVariant(mdVersion: number): mdVersion is StartBuffCardVersion {
  return mdVersion in CARD_VARIANTS
}

/** Narrows an arbitrary MD version to one the card can actually draw. */
export function resolveStartBuffCardVersion(mdVersion: number): StartBuffCardVersion {
  return hasCardVariant(mdVersion) ? mdVersion : FALLBACK_MD_VERSION
}
