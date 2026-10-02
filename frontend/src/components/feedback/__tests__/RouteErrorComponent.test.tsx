import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { RouteErrorComponent } from '../RouteErrorComponent'
import { NotFoundError } from '@/lib/apiErrors'
import { markChunkLoadError } from '@/lib/routeErrorCode'
import { mountRoute } from '@/test-utils/mountRoute'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

describe('RouteErrorComponent', () => {
  const reset = vi.fn()
  const reload = vi.fn()

  function renderError(error: unknown, url = '/') {
    return mountRoute('$', url, () => <RouteErrorComponent error={error as Error} reset={reset} />)
  }

  beforeEach(() => {
    vi.stubEnv('DEV', false)
    vi.spyOn(window.location, 'reload').mockImplementation(reload)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('shows the generic message and the error code in production', async () => {
    renderError(new TypeError('boom'))

    expect(await screen.findByText('errors.generic.message')).toBeInTheDocument()
    expect(screen.getByText('UNKNOWN · TypeError')).toBeInTheDocument()
    expect(screen.queryByText('boom')).not.toBeInTheDocument()
  })

  it('shows the error code beside the dev message in development', async () => {
    vi.stubEnv('DEV', true)
    renderError(new TypeError('boom'))

    expect(await screen.findByText('boom')).toBeInTheDocument()
    expect(screen.getByText('UNKNOWN · TypeError')).toBeInTheDocument()
  })

  it('resets the router when the error is not a chunk failure', async () => {
    const user = userEvent.setup()
    renderError(new TypeError('boom'))

    await user.click(await screen.findByRole('button', { name: 'errors.generic.retry' }))

    expect(reset).toHaveBeenCalledTimes(1)
    expect(reload).not.toHaveBeenCalled()
  })

  it('reloads the page when the error is a chunk failure', async () => {
    const user = userEvent.setup()
    const error = new TypeError('Failed to fetch dynamically imported module')
    markChunkLoadError(error)
    renderError(error)

    expect(await screen.findByText('CHUNK_LOAD')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'errors.generic.retry' }))

    expect(reload).toHaveBeenCalledTimes(1)
    expect(reset).not.toHaveBeenCalled()
  })

  it.each([
    { url: '/somewhere', title: 'errors.notFound.title' },
    { url: '/planner/md/abc', title: 'pages.detail.notFound' },
    { url: '/planner/md/gesellschaft/abc', title: 'pages.detail.notFound' },
  ])('renders the not-found page without a code at $url', async ({ url, title }) => {
    const { container } = renderError(new NotFoundError('missing'), url)

    expect(await screen.findByText(title)).toBeInTheDocument()
    expect(container.querySelector('code')).toBeNull()
    expect(screen.queryByText(/UNKNOWN/)).not.toBeInTheDocument()
  })
})
