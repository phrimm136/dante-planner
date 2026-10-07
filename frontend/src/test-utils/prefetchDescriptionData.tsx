import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { Suspense } from 'react'

import { entityListI18nOptions, entityListSpecOptions } from '@/shared/entityCatalog'
import { FormattedDescription, KEYWORD_LIST } from '@/shared/gameText'

const WARMING_TEST_ID = 'description-data-warming'
const WARM_UP_TIMEOUT_MS = 10_000

/**
 * Loads every static-data query a description boundary reads into `queryClient`, so a boundary
 * mounted later renders without suspending. The skill-tag and color-code queries have no public
 * options factory, so one mounted description reads them through the same keys the page uses.
 */
export async function prefetchDescriptionData(
  queryClient: QueryClient,
  language: string,
): Promise<void> {
  await Promise.all([
    queryClient.ensureQueryData(entityListSpecOptions(KEYWORD_LIST)),
    queryClient.ensureQueryData(entityListI18nOptions(KEYWORD_LIST, language)),
  ])
  const { unmount } = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<span data-testid={WARMING_TEST_ID} />}>
        <FormattedDescription text="warm" />
      </Suspense>
    </QueryClientProvider>,
  )
  await waitFor(
    () => {
      if (screen.queryByTestId(WARMING_TEST_ID)) throw new Error('description data still loading')
    },
    { timeout: WARM_UP_TIMEOUT_MS },
  )
  unmount()
}
