export { ColoredText, parseColorTags, stripColorTags } from './components/ColoredText'
export { FormattedDescription } from './components/FormattedDescription'
export { FormattedSanityText } from './components/FormattedSanityText'
export { StyledSkillName, StyledNameSkeleton } from './components/StyledName'

export { useColorCodes, getColorForAttributeType } from './hooks/useColorCodes'
export { useSanityConditionI18n } from './hooks/useSanityConditionData'
export { useBattleKeywords, getKeywordName } from './hooks/useBattleKeywords'
export {
  keywordListQueryKeys,
  useKeywordListSpec,
  useKeywordListI18n,
  useKeywordListI18nDeferred,
  useKeywordListData,
} from './hooks/useKeywordListData'

export { applyStrikethrough } from './lib/unityRichText'

export { ColorCodeMapSchema } from './schemas/ColorCodeSchemas'
export { SanityConditionI18nSchema } from './schemas/SanityConditionSchemas'
export type { SanityConditionI18n } from './schemas/SanityConditionSchemas'
export { BattleKeywordsSchema, BattleKeywordEntrySchema } from './schemas/BattleKeywordsSchemas'
export {
  BattleKeywordSpecEntrySchema,
  BattleKeywordSpecListSchema,
  BattleKeywordNameListSchema,
} from './schemas/KeywordSchemas'
export { StartBuffDataListSchema, StartBuffI18nSchema } from './schemas/StartBuffSchemas'

export {
  BASE_BUFF_IDS,
  createBuffId,
  deriveEnhancements,
  getBaseIdFromBuffId,
  getEnhancementFromBuffId,
  getEnhancementSuffix,
} from './types/StartBuffTypes'
export type {
  StartBuff,
  StartBuffI18n,
  StartBuffDataList,
  BattleKeywords,
  BattleKeywordI18nEntry,
  EnhancementLevel,
  BuffEffect,
} from './types/StartBuffTypes'
export type { BattleKeywordSpecEntry } from './types/KeywordTypes'
