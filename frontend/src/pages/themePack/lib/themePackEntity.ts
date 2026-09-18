import { createEntityBuilder } from '@/shared/filter'
import { ThemePackIdSchema } from '@/shared/gameData'

import type { ThemePackId } from '@/shared/gameData'
import type { ThemePackSpec } from '../types/ThemePackTypes'

export const toThemePackEntity = createEntityBuilder<ThemePackId, ThemePackSpec>(ThemePackIdSchema)
