import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { TimeoutDialog } from '../BanDialog'

describe('TimeoutDialog — duration presets', () => {
  it('names the preset group by the duration label', () => {
    render(<TimeoutDialog open onOpenChange={vi.fn()} username="dante" onConfirm={vi.fn()} />)

    const group = screen.getByRole('group', { name: 'Timeout Duration' })
    expect(within(group).getAllByRole('button').length).toBeGreaterThan(0)
  })
})
