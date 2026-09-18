export type KeywordType = 'battleKeyword' | 'skillTag' | 'unknown'

/**
 * Buff type for coloring keywords
 * Maps to colorCode entries for styling
 *
 * Deliberately an OPEN union: pipeline data may carry buffType values beyond
 * the three known ones, so this must stay hand-written — do not narrow to an enum.
 */
export type BuffType = 'Positive' | 'Negative' | 'Neutral' | (string & {})

export type { BattleKeywordSpec } from '../schemas/KeywordSchemas'

export interface ResolvedKeyword {
  type: KeywordType
  key: string
  displayText: string
  description?: string
  flavor?: string
  iconId?: string | null
  buffType?: BuffType
  color?: string
}

export interface ParsedSegment {
  type: 'text' | 'keyword'
  content: string
  keyword?: ResolvedKeyword
}

export interface KeywordResolutionContext {
  battleKeywords: Record<
    string,
    {
      name: string
      desc: string
      flavor?: string
      iconId: string | null
      buffType: string
    }
  >
  skillTags: Record<string, string>
  colorCodes: Record<string, string>
}
