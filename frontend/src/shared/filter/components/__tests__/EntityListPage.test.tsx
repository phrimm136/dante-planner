import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { lazy } from 'react'

import { EntityListPage } from '../EntityListPage'

describe('EntityListPage', () => {
  it('wraps children in the page container', () => {
    const { container } = render(
      <EntityListPage skeleton={<div data-testid="skeleton" />}>
        <p data-testid="shell">shell</p>
      </EntityListPage>,
    )

    expect(container.firstElementChild).toHaveClass('container', 'mx-auto', 'p-8')
    expect(screen.getByTestId('shell')).toBeInTheDocument()
    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument()
  })

  it('shows the skeleton while the shell suspends', async () => {
    let resolveShell: () => void = () => {}
    const pending = new Promise<void>((resolve) => {
      resolveShell = resolve
    })
    const Shell = lazy(async () => {
      await pending
      return { default: () => <p data-testid="shell">shell</p> }
    })

    render(
      <EntityListPage skeleton={<div data-testid="skeleton" />}>
        <Shell />
      </EntityListPage>,
    )

    expect(screen.getByTestId('skeleton')).toBeInTheDocument()

    resolveShell()
    await waitFor(() => {
      expect(screen.getByTestId('shell')).toBeInTheDocument()
    })
  })
})
