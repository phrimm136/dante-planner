import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { BattleKeywordId } from '@/shared/gameData'
import type { BattleKeywordSpec, BattleKeywordSpecListSchema } from '@/shared/gameText'

export type BattleKeywordSpecList = z.infer<typeof BattleKeywordSpecListSchema>

export type KeywordEntity = Entity<BattleKeywordId, BattleKeywordSpec>
