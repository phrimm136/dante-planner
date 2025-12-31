import { useSuspenseQuery, queryOptions } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { SanityConditionI18nSchema } from '@/schemas/SanityConditionSchemas';
import type { SanityConditionI18n } from '@/schemas/SanityConditionSchemas';

/**
 * Query keys for sanity condition i18n data
 */
export const sanityConditionQueryKeys = {
  i18n: (language: string) => ['sanityCondition', 'i18n', language] as const,
};

/**
 * Query options for sanity condition i18n with runtime validation
 */
function createSanityConditionI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: sanityConditionQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/sanityCondition.json`);
      const result = SanityConditionI18nSchema.safeParse(module.default);
      if (!result.success) {
        throw new Error(`[sanityCondition i18n / ${language}] Validation failed: ${result.error.message}`);
      }
      return result.data;
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  });
}

/**
 * Hook that loads and validates sanity condition i18n data.
 * Suspends while loading - wrap in Suspense boundary.
 *
 * Returns the raw i18n data for manual formatting.
 * For formatting, use useSanityConditionFormatter from lib/sanityConditionFormatter.ts
 *
 * @returns Validated sanity condition i18n data
 */
export function useSanityConditionI18n(): SanityConditionI18n {
  const { i18n } = useTranslation();
  const { data } = useSuspenseQuery(createSanityConditionI18nQueryOptions(i18n.language));
  return data;
}
