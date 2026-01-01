/**
 * Keyword Formatting Types
 *
 * Types for parsing and rendering [BracketedKeywords] in skill/passive descriptions.
 * Supports battle keywords (with icons/popovers) and skill tags (styled text only).
 */

/**
 * Type of keyword found in description text
 * - battleKeyword: Has icon, description, and click popover
 * - skillTag: Styled display text only (no interaction)
 * - unknown: Not found in any data source (rendered as plain text)
 */
export type KeywordType = 'battleKeyword' | 'skillTag' | 'unknown'

/**
 * Buff type for coloring keywords
 * Maps to colorCode entries for styling
 */
export type BuffType = 'Positive' | 'Negative' | 'Neutral' | string

/**
 * Resolved keyword with all data needed for rendering
 */
export interface ResolvedKeyword {
  /** Type of keyword for rendering logic */
  type: KeywordType
  /** Original key from brackets (e.g., "Sinking") */
  key: string
  /** Translated display text (e.g., "Sinking" or "[On Hit]") */
  displayText: string
  /** Description for popover (battle keywords only) */
  description?: string
  /** Icon ID for image path (battle keywords only) */
  iconId?: string | null
  /** Buff type for color lookup (Positive/Negative/Neutral) */
  buffType?: BuffType
  /** Resolved color from colorCode (e.g., "#ff6b6b") */
  color?: string
}

/**
 * Parsed segment of description text
 * Either plain text or a keyword reference
 */
export interface ParsedSegment {
  /** Segment type for rendering */
  type: 'text' | 'keyword'
  /** Raw content (plain text or keyword key) */
  content: string
  /** Resolved keyword data (only for keyword segments) */
  keyword?: ResolvedKeyword
}

/**
 * Context for keyword resolution
 * Contains all data sources needed to resolve keywords
 */
export interface KeywordResolutionContext {
  /** Battle keywords dictionary (translated) */
  battleKeywords: Record<string, {
    name: string
    desc: string
    iconId: string | null
    buffType: string
  }>
  /** Skill tags dictionary (translated) */
  skillTags: Record<string, string>
  /** Color codes for styling */
  colorCodes: Record<string, string>
}
