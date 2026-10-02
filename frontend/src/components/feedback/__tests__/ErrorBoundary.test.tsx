import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen } from '@testing-library/react'
import { ErrorBoundary } from '../ErrorBoundary'
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

function Thrower({ error }: { error: unknown }): never {
  throw error
}

function renderThrowing(error: unknown) {
  return mountRoute('/', '/', () => (
    <ErrorBoundary>
      <Thrower error={error} />
    </ErrorBoundary>
  ))
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.stubEnv('DEV', false)
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('shows the generic message and the error code in production', async () => {
    renderThrowing(new TypeError('boom'))

    expect(await screen.findByText('errors.generic.message')).toBeInTheDocument()
    expect(screen.getByText('UNKNOWN · TypeError')).toBeInTheDocument()
    expect(screen.queryByText('boom')).not.toBeInTheDocument()
  })

  it('shows CHUNK_LOAD for a stamped chunk failure', async () => {
    const error = new TypeError('Failed to fetch dynamically imported module')
    markChunkLoadError(error)
    renderThrowing(error)

    expect(await screen.findByText('CHUNK_LOAD')).toBeInTheDocument()
  })

  it('shows UNKNOWN alone for a thrown non-Error value', async () => {
    renderThrowing('str')

    expect(await screen.findByText('UNKNOWN')).toBeInTheDocument()
  })

  it('renders the not-found page without a code', async () => {
    const { container } = renderThrowing(new NotFoundError('missing'))

    expect(await screen.findByText('errors.notFound.title')).toBeInTheDocument()
    expect(container.querySelector('code')).toBeNull()
  })
})
