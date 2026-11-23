import { afterEach, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'

import { ApiClient } from '@/lib/api'
import { UnauthorizedError } from '@/lib/apiErrors'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { server } from '@/test-utils/mswServer'
import { authQueryKeys } from '../../hooks/useAuthQuery'
import { clearSessionOnUnauthorized } from '../clearSessionOnUnauthorized'

describe('clearSessionOnUnauthorized', () => {
  const cleanups: (() => void)[] = []

  afterEach(() => {
    cleanups.splice(0).forEach((cleanup) => cleanup())
  })

  it('clears the signed-in user after a 401 through the client', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(authQueryKeys.me, { email: 'test@example.com' })
    cleanups.push(clearSessionOnUnauthorized(queryClient))
    server.use(
      http.get(
        'http://localhost:8080/api/planner/md',
        () => new HttpResponse(null, { status: 401 }),
      ),
    )

    await expect(ApiClient.get('/api/planner/md')).rejects.toBeInstanceOf(UnauthorizedError)

    expect(queryClient.getQueryData(authQueryKeys.me)).toBeNull()
  })
})
