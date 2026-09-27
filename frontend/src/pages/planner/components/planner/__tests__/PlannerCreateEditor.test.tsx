import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PlannerCreateEditor } from '../PlannerCreateEditor'

vi.mock('../PlannerEditorShell', () => ({
  PlannerEditorShell: ({ contentVersion }: { contentVersion: number }) => (
    <div data-testid="shell">{contentVersion}</div>
  ),
}))

describe('PlannerCreateEditor', () => {
  it('starts a new planner at the current season from plannerVersions.json', () => {
    render(<PlannerCreateEditor />)

    expect(screen.getByTestId('shell').textContent).toBe('7')
  })
})
