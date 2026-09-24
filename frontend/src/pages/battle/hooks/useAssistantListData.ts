import type { z } from 'zod'
import {
  useEntityListI18n,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import { AssistantNameListSchema, AssistantSpecListSchema } from '../schemas/AssistantSchemas'

export const ASSISTANT_LIST: EntityListDataConfig<
  z.infer<typeof AssistantSpecListSchema>,
  z.infer<typeof AssistantNameListSchema>
> = {
  kind: 'assistant',
  specImport: () => import('@static/data/assistantSpecList.json'),
  specSchema: AssistantSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/assistantNameList.json`),
  i18nSchema: AssistantNameListSchema,
}

export function useAssistantListSpec() {
  return useEntityListSpec(ASSISTANT_LIST)
}

export function useAssistantListI18n() {
  return useEntityListI18n(ASSISTANT_LIST)
}
