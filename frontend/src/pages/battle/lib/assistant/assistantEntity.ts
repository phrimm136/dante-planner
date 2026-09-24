import { createEntityBuilder } from '@/shared/filter'
import { AssistantIdSchema } from '@/shared/gameData'

import type { AssistantId } from '@/shared/gameData'
import type { AssistantSpec } from '../../types/AssistantTypes'

export const toAssistantEntity = createEntityBuilder<AssistantId, AssistantSpec>(AssistantIdSchema)
