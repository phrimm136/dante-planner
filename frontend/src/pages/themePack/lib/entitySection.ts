import type { EntitySection } from '@/shared/filter'
import { ThemePackIdSchema } from '@/shared/gameData'

import type { ThemePackId } from '@/shared/gameData'
import { ThemePackSpecSchema, ThemePackListSchema } from '../schemas/ThemePackSchemas'
import type { ThemePackSpec } from '../types/ThemePackTypes'
import { THEME_PACK_FACETS } from './themePackFilter'
import type { ThemePackFacetState } from './themePackFilter'
import { toThemePackEntity } from './themePackEntity'

export const section: EntitySection<ThemePackId, ThemePackSpec, ThemePackFacetState> = {
  name: 'themePack',
  specFile: 'themePackList.json',
  specListSchema: ThemePackListSchema,
  specEntrySchema: ThemePackSpecSchema,
  idSchema: ThemePackIdSchema,
  toEntity: toThemePackEntity,
  facets: THEME_PACK_FACETS,
}
