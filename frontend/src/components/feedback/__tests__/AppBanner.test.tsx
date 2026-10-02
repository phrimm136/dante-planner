import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { QueryClientProvider, useMutation, useQuery, type QueryClient } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { toast } from 'sonner'

import { ApiClient } from '@/lib/api'
import { useServerStatusStore } from '@/lib/banner/serverStatus'
import type { BannerSignal } from '@/lib/banner/selectBanner'
import { BANNER_DISMISS_STORAGE_KEY } from '@/lib/constants'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import { server } from '@/test-utils/mswServer'
import { createTestQueryClient } from '@/test-utils/queryClient'

import { AppBanner } from '../AppBanner'

const extra = vi.hoisted(() => ({ signals: [] as BannerSignal[] }))

vi.mock('@/lib/banner/useBannerSignals', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/banner/useBannerSignals')>()
  return {
    useBannerSignals: () => [...actual.useBannerSignals(), ...extra.signals],
  }
})

vi.mock('@/lib/banner/useStorageUnavailable', () => ({ useStorageUnavailable: () => false }))

vi.mock('@/lib/banner/useUpdateAvailable', () => ({ useUpdateAvailable: () => null }))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@/lib/i18n', () => ({
  default: { t: (key: string) => key },
}))

vi.mock('@/components/ui/LinkifyText', () => ({
  linkifyText: (text: string) => text,
}))

vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    dismiss: vi.fn(),
  })
  return { toast: toastFn }
})

const storage: BannerSignal = { kind: 'storageUnavailable' }
const update: BannerSignal = { kind: 'updateAvailable', buildId: 'build-x' }

function QueryProbe({ path }: { path: string }) {
  useQuery({
    queryKey: ['probe', path],
    queryFn: ({ signal }) => ApiClient.get(path, { signal }),
  })
  return null
}

function MutationProbe() {
  const mutation = useMutation({ mutationFn: () => ApiClient.post('/api/probe/write', {}) })
  return (
    <button type="button" onClick={() => mutation.mutate()}>
      write
    </button>
  )
}

function AuthProbe() {
  const { data, isSuccess } = useAuthQueryNonBlocking()
  return isSuccess ? <span>auth:{data === null ? 'null' : 'user'}</span> : null
}

function renderBanner(children?: React.ReactNode, client: QueryClient = createTestQueryClient()) {
  return render(
    <QueryClientProvider client={client}>
      <AppBanner />
      {children}
    </QueryClientProvider>,
  )
}

function unavailable(code?: string) {
  return () => HttpResponse.json(code === undefined ? {} : { code }, { status: 503 })
}

describe('AppBanner', () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: 'error' })
  })

  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    useServerStatusStore.setState({ status: 'ok' })
    extra.signals = []
    sessionStorage.clear()
  })

  afterEach(() => {
    server.resetHandlers()
    vi.restoreAllMocks()
  })

  afterAll(() => {
    server.close()
    globalThis.fetch = vi.fn() as unknown as typeof fetch
  })

  it('renders nothing while no condition holds', () => {
    renderBanner()

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows one banner and no toast when several queries meet a database outage', async () => {
    server.use(http.get('*/api/probe/:name', unavailable('WRITE_TEMPORARILY_UNAVAILABLE')))
    const client = createTestQueryClient()

    renderBanner(
      <>
        <QueryProbe path="/api/probe/a" />
        <QueryProbe path="/api/probe/b" />
        <QueryProbe path="/api/probe/c" />
      </>,
      client,
    )

    await waitFor(() =>
      expect(
        client.getQueryCache().findAll({ predicate: (q) => q.state.status === 'error' }),
      ).toHaveLength(3),
    )
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent('errors.backendUnavailable')
    expect(toast.warning).not.toHaveBeenCalled()
  })

  it('clears once any request succeeds', async () => {
    useServerStatusStore.setState({ status: 'down' })
    server.use(http.get('*/api/probe/ok', () => HttpResponse.json({})))

    renderBanner(<QueryProbe path="/api/probe/ok" />)

    expect(screen.getByRole('status')).toHaveTextContent('errors.backendUnavailable')
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
  })

  it('still toasts a failed mutation while the banner shows', async () => {
    server.use(http.post('*/api/probe/write', unavailable()))
    const user = userEvent.setup()

    renderBanner(<MutationProbe />)
    await user.click(screen.getByRole('button', { name: 'write' }))

    await waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith('common:errors.backendUnavailable', undefined),
    )
    expect(screen.getByRole('status')).toHaveTextContent('errors.backendUnavailable')
  })

  it('shows the banner although the auth query resolves through the outage', async () => {
    server.use(http.get('*/api/auth/me', unavailable()))

    renderBanner(<AuthProbe />)

    expect(await screen.findByText('auth:null')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('errors.backendUnavailable')
  })

  it('renders only the highest-priority signal', () => {
    useServerStatusStore.setState({ status: 'down' })
    extra.signals = [update, storage]

    renderBanner()

    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent('errors.backendUnavailable')
    expect(screen.queryByRole('button', { name: 'banner.dismiss' })).not.toBeInTheDocument()
  })

  it('dismisses storage, shows the next signal, and keeps it dismissed across a remount', async () => {
    extra.signals = [storage, update]
    const user = userEvent.setup()

    const { unmount } = renderBanner()
    expect(screen.getByRole('status')).toHaveTextContent('banner.storageUnavailable')

    await user.click(screen.getByRole('button', { name: 'banner.dismiss' }))
    expect(screen.getByRole('status')).toHaveTextContent('banner.updateAvailable')

    unmount()
    renderBanner()

    expect(screen.getByRole('status')).toHaveTextContent('banner.updateAvailable')
    expect(sessionStorage.getItem(BANNER_DISMISS_STORAGE_KEY)).toBe('["storageUnavailable"]')
  })

  it('dismisses an update for its build only', async () => {
    extra.signals = [update]
    const user = userEvent.setup()

    const { unmount } = renderBanner()
    await user.click(screen.getByRole('button', { name: 'banner.dismiss' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    unmount()
    extra.signals = [{ kind: 'updateAvailable', buildId: 'build-y' }]
    renderBanner()

    expect(screen.getByRole('status')).toHaveTextContent('banner.updateAvailable')
  })

  it('reloads the page from the update banner', async () => {
    const reload = vi.fn()
    vi.spyOn(window.location, 'reload').mockImplementation(reload)
    extra.signals = [update]
    const user = userEvent.setup()

    renderBanner()
    await user.click(screen.getByRole('button', { name: 'banner.reload' }))

    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('keeps dismissal in memory when session storage throws', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    extra.signals = [storage]
    const user = userEvent.setup()

    renderBanner()
    await user.click(screen.getByRole('button', { name: 'banner.dismiss' }))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
