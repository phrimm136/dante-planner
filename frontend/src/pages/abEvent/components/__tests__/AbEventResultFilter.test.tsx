import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { AB_EVENT_RESULT_TYPES } from '@/shared/gameData'
import { AbEventResultFilter } from '../AbEventResultFilter'

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

function labelOf(resultType: string): string {
  return i18n.t(`filters.eventResultTypes.${resultType}`, { ns: 'database' })
}

describe('AbEventResultFilter', () => {
  it('shows the nine kinds in the fixed order, labelled from database i18n', () => {
    render(<AbEventResultFilter selected={new Set()} onSelectionChange={vi.fn()} />)

    for (const resultType of AB_EVENT_RESULT_TYPES) {
      expect(i18n.exists(`filters.eventResultTypes.${resultType}`, { ns: 'database' })).toBe(true)
    }
    const buttons = screen.getAllByRole('button')
    expect(buttons.map((button) => button.textContent)).toEqual(AB_EVENT_RESULT_TYPES.map(labelOf))
    for (const button of buttons) {
      expect(button).toHaveAttribute('aria-pressed', 'false')
    }
  })

  it('reports a set holding the clicked kind', () => {
    const onSelectionChange = vi.fn()
    render(<AbEventResultFilter selected={new Set()} onSelectionChange={onSelectionChange} />)

    fireEvent.click(screen.getByRole('button', { name: labelOf('levelUp') }))

    expect(onSelectionChange).toHaveBeenCalledOnce()
    expect(onSelectionChange).toHaveBeenCalledWith(new Set(['levelUp']))
  })
})
