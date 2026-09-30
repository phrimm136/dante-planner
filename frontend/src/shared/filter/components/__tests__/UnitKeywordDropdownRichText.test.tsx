import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ASSOCIATIONS } from '@/shared/gameData'
import { UnitKeywordDropdown } from '../UnitKeywordDropdown'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => fallback ?? key,
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('@/shared/filter/hooks/useFilterI18nData', () => {
  const [colored, sized] = ASSOCIATIONS
  if (colored === undefined || sized === undefined) {
    throw new Error('ASSOCIATIONS carries fewer than the two keywords these labels need')
  }

  return {
    useFilterI18nData: () => ({
      seasonsI18n: {},
      unitKeywordsI18n: {
        ...Object.fromEntries(ASSOCIATIONS.map((a) => [a, `Label_${a}`])),
        [colored]: '<color=#d40000><s>Jia Family</s></color>',
        [sized]: 'サ<size=50%>ル</size>党派',
      },
    }),
  }
})

describe('UnitKeywordDropdown rich text', () => {
  it('renders option labels as TextMeshPro runs', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown selected={new Set()} onSelectionChange={vi.fn()} />)

    await user.click(screen.getByRole('combobox'))

    const struck = screen.getByText('Jia Family')
    expect(struck.style.getPropertyValue('--rt-color')).toBe('#d40000')
    expect(struck.style.textDecorationLine).toBe('line-through')
    expect(screen.getByText('ル').style.fontSize).toBe('50%')
  })

  it('searches on the tag-free label', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown selected={new Set()} onSelectionChange={vi.fn()} />)

    await user.click(screen.getByRole('combobox'))
    await user.type(screen.getByPlaceholderText('Search Unit Keywords...'), 'サル党派')

    expect(screen.getAllByRole('option')).toHaveLength(1)
  })
})
