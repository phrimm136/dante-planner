export type ResistLevel = 'immune' | 'ineffective' | 'endure' | 'normal' | 'weak' | 'fatal'

export const RESIST_LEVEL_COLOURS: Record<ResistLevel | 'blank', string> = {
  immune: '#808080',
  ineffective: '#808080',
  endure: '#CCCCCC',
  normal: '#E4C393',
  weak: '#FF8D8D',
  fatal: '#FF4040',
  blank: '#E4C393',
}

const IMMUNE_MAX = 0
const INEFFECTIVE_MAX = 0.51
const ENDURE_BELOW = 0.99
const NORMAL_BELOW = 1.01
const WEAK_MAX = 1.51

export function resistLevel(v: number): ResistLevel {
  if (v <= IMMUNE_MAX) return 'immune'
  if (v <= INEFFECTIVE_MAX) return 'ineffective'
  if (v < ENDURE_BELOW) return 'endure'
  if (v < NORMAL_BELOW) return 'normal'
  if (v <= WEAK_MAX) return 'weak'
  return 'fatal'
}
