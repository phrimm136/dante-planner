import { describe, it, expect, beforeAll, beforeEach } from 'vitest'
import { Suspense } from 'react'
import { render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'

import i18n from '@/lib/i18n'
import { queryClient } from '@/lib/queryClient'
import { loadIdentityDetail } from '@/lib/routeLoaders'
import { identityDetailQueryKeys, useIdentityDetailSpec } from '../useIdentityDetailData'

const IDENTITY_ID = '10101'

function SpecProbe() {
  const spec = useIdentityDetailSpec(IDENTITY_ID)
  return <span>{`rank ${spec.rank}`}</span>
}

beforeAll(async () => {
  await i18n.changeLanguage('EN')
})

beforeEach(() => {
  queryClient.clear()
})

describe('loadIdentityDetail', () => {
  it('caches the spec under the key the detail page reads, so the page renders without refetching', async () => {
    const loaded = await loadIdentityDetail({ params: { id: IDENTITY_ID } })

    expect(loaded.name).not.toBe(IDENTITY_ID)
    const detailKey = identityDetailQueryKeys.detail(IDENTITY_ID)
    expect(queryClient.getQueryState(detailKey)?.dataUpdateCount).toBe(1)

    render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<span>suspended</span>}>
          <SpecProbe />
        </Suspense>
      </QueryClientProvider>,
    )

    expect(screen.queryByText('suspended')).toBeNull()
    expect(screen.getByText(/^rank \d+$/)).toBeTruthy()
    expect(queryClient.getQueryState(detailKey)?.dataUpdateCount).toBe(1)
    expect(queryClient.isFetching({ queryKey: detailKey })).toBe(0)
  })
})
