import { useBattleKeywords } from './useBattleKeywords'
import { useSkillTagI18n } from './useSkillTagI18n'
import { useColorCodes } from './useColorCodes'
import { formatDescription, parseKeywords, resolveKeyword } from '../lib/keywordFormatter'
import type {
  ParsedSegment,
  KeywordResolutionContext,
  ResolvedKeyword,
} from '../types/KeywordTypes'

export function useKeywordFormatter() {
  const { data: battleKeywords } = useBattleKeywords()
  const { data: skillTags } = useSkillTagI18n()
  const { data: colorCodes } = useColorCodes()

  const context: KeywordResolutionContext = {
    battleKeywords,
    skillTags,
    colorCodes,
  }

  return {
    format: (text: string): ParsedSegment[] => {
      return formatDescription(text, context)
    },

    parse: (text: string): ParsedSegment[] => {
      return parseKeywords(text)
    },

    resolve: (key: string): ResolvedKeyword => {
      return resolveKeyword(key, context)
    },

    context,
  }
}
