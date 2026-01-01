/**
 * Keyword Formatter Hook
 *
 * Provides formatting functions for [BracketedKeywords] in skill/passive descriptions.
 * Combines battle keywords, skill tags, and color codes into a single formatting interface.
 * Separated from pure functions following Single Responsibility Principle.
 */

import { useBattleKeywords } from '@/hooks/useBattleKeywords'
import { useSkillTagI18n } from '@/hooks/useSkillTagI18n'
import { useColorCodes } from '@/hooks/useColorCodes'
import { formatDescription, parseKeywords, resolveKeyword } from '@/lib/keywordFormatter'
import type { ParsedSegment, KeywordResolutionContext, ResolvedKeyword } from '@/types/KeywordTypes'

/**
 * Hook that provides formatter functions for skill/passive description keywords.
 * Suspends while loading data - wrap in Suspense boundary.
 *
 * Returns functions to format description text with automatic language reactivity.
 * Keywords are resolved against battle keywords and skill tags data sources.
 *
 * @returns Formatter functions and resolution context
 *
 * @example
 * ```tsx
 * function SkillDescription({ text }: { text: string }) {
 *   const { format } = useKeywordFormatter();
 *
 *   // Format entire description
 *   const segments = format("Apply 2 [Sinking] [OnSucceedAttack]");
 *   // => [
 *   //   { type: 'text', content: 'Apply 2 ' },
 *   //   { type: 'keyword', content: 'Sinking', keyword: { type: 'battleKeyword', ... } },
 *   //   { type: 'keyword', content: 'OnSucceedAttack', keyword: { type: 'skillTag', ... } }
 *   // ]
 *
 *   return (
 *     <span>
 *       {segments.map((seg, i) =>
 *         seg.type === 'text'
 *           ? seg.content
 *           : <FormattedKeyword key={i} keyword={seg.keyword!} />
 *       )}
 *     </span>
 *   );
 * }
 * ```
 */
export function useKeywordFormatter() {
  const { data: battleKeywords } = useBattleKeywords()
  const { data: skillTags } = useSkillTagI18n()
  const { data: colorCodes } = useColorCodes()

  // Build resolution context from all data sources
  const context: KeywordResolutionContext = {
    battleKeywords,
    skillTags,
    colorCodes,
  }

  return {
    /**
     * Format a description text, resolving all [BracketedKeywords]
     * @param text - Raw description text with brackets
     * @returns Array of parsed segments with resolved keyword data
     */
    format: (text: string): ParsedSegment[] => {
      return formatDescription(text, context)
    },

    /**
     * Parse text into segments without resolving keywords
     * Useful for pre-processing or debugging
     * @param text - Raw description text
     * @returns Array of parsed segments (text or keyword)
     */
    parse: (text: string): ParsedSegment[] => {
      return parseKeywords(text)
    },

    /**
     * Resolve a single keyword key to full rendering data
     * @param key - Keyword key (without brackets)
     * @returns Resolved keyword with type, display text, color, etc.
     */
    resolve: (key: string): ResolvedKeyword => {
      return resolveKeyword(key, context)
    },

    /** Resolution context for advanced usage */
    context,
  }
}
