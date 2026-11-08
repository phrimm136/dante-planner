import { Suspense, type ComponentType } from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErrorBoundary } from 'react-error-boundary'
import { lazyRoute } from '../lazyRoute'

const MODULE_NOT_FOUND = 'Failed to fetch dynamically imported module: http://localhost/a/x.js'

function renderInBoundary<P extends object>(Component: ComponentType<P>, props: P): unknown[] {
  const caught: unknown[] = []
  render(
    <ErrorBoundary fallbackRender={() => <p>boundary</p>} onError={(error) => caught.push(error)}>
      <Suspense fallback={<p>suspended</p>}>
        <Component {...props} />
      </Suspense>
    </ErrorBoundary>,
  )
  return caught
}

describe('lazyRoute', () => {
  const reload = vi.fn()

  beforeEach(() => {
    sessionStorage.clear()
    reload.mockReset()
    vi.spyOn(window.location, 'reload').mockImplementation(reload)
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('hands a module-not-found rejection to the boundary without reloading', async () => {
    const error = new TypeError(MODULE_NOT_FOUND)
    const Route = lazyRoute(() => Promise.reject<{ default: () => null }>(error))

    await Route.preload?.()
    const caught = renderInBoundary(Route, {})

    expect(reload).not.toHaveBeenCalled()
    await screen.findByText('boundary')
    expect(caught).toHaveLength(1)
    expect(caught[0]).toBe(error)
  })

  it('renders the default export after preload', async () => {
    const importer = vi.fn(() =>
      Promise.resolve({ default: ({ label }: { label: string }) => <p>{label}</p> }),
    )
    const Route = lazyRoute(importer)

    await Route.preload?.()
    renderInBoundary(Route, { label: 'ready' })

    expect(await screen.findByText('ready')).toBeInTheDocument()
    expect(importer).toHaveBeenCalledOnce()
  })

  it('renders the named export when one is given', async () => {
    const Route = lazyRoute(() => Promise.resolve({ Page: () => <p>named</p> }), 'Page')

    await Route.preload?.()
    renderInBoundary(Route, {})

    expect(await screen.findByText('named')).toBeInTheDocument()
  })

  it('retries a failed import on the next preload', async () => {
    const importer = vi
      .fn<() => Promise<{ default: () => React.JSX.Element }>>()
      .mockRejectedValueOnce(new TypeError(MODULE_NOT_FOUND))
      .mockResolvedValueOnce({ default: () => <p>recovered</p> })
    const Route = lazyRoute(importer)

    await Route.preload?.()
    renderInBoundary(Route, {})
    await screen.findByText('boundary')

    await Route.preload?.()
    renderInBoundary(Route, {})

    expect(await screen.findByText('recovered')).toBeInTheDocument()
    expect(importer).toHaveBeenCalledTimes(2)
  })

  it('rethrows the stored error on every render without importing again', async () => {
    const error = new TypeError(MODULE_NOT_FOUND)
    const importer = vi.fn(() => Promise.reject<{ default: () => null }>(error))
    const Route = lazyRoute(importer)

    await Route.preload?.()
    const first = renderInBoundary(Route, {})
    const second = renderInBoundary(Route, {})

    expect(await screen.findAllByText('boundary')).toHaveLength(2)
    expect(importer).toHaveBeenCalledOnce()
    expect(first).toEqual([error])
    expect(second).toEqual([error])
    expect(first[0]).toBe(error)
    expect(second[0]).toBe(error)
  })
})
