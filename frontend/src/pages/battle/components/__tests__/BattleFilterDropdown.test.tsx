import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen } from '@testing-library/react'

import enemySpec from '@static/data/enemySpecList.json'
import assistantSpec from '@static/data/assistantSpecList.json'
import sinnerEventSpec from '@static/data/sinnerEventSpecList.json'
import enemyNamesEN from '@static/i18n/EN/enemyNameList.json'
import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import type { BattleFamily } from '../../lib/battleVocabulary'
import { BattleFilterDropdown } from '../BattleFilterDropdown'

vi.mock('@/shared/filter', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/filter')>()),
  SearchableMultiSelect: ({
    options,
    placeholder,
    selectedValues,
  }: {
    options: { value: string; label: string }[]
    placeholder: string
    selectedValues: Set<string>
  }) => (
    <div>
      <span data-testid="placeholder">{placeholder}</span>
      <span data-testid="selected-count">{selectedValues.size}</span>
      <ul>
        {options.map((option) => (
          <li key={option.value} data-testid="option" data-value={option.value}>
            {option.label}
          </li>
        ))}
      </ul>
    </div>
  ),
}))

const CARD_IDS: Record<BattleFamily, string[]> = {
  enemy: Object.keys(enemySpec),
  assistant: Object.keys(assistantSpec),
  sinnerEvent: Object.keys(sinnerEventSpec),
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('BattleFilterDropdown', () => {
  it.each(Object.keys(CARD_IDS) as BattleFamily[])('offers every %s card', async (family) => {
    renderWithProviders(
      <BattleFilterDropdown
        family={family}
        selected={new Set()}
        onSelectionChange={vi.fn()}
        placeholderKey="keyword.filterIdentity"
      />,
    )

    const options = await screen.findAllByTestId('option')
    expect(options).toHaveLength(CARD_IDS[family].length)
    expect(new Set(options.map((option) => option.dataset.value))).toEqual(
      new Set(CARD_IDS[family]),
    )
  })

  it('labels an enemy option with its card name', async () => {
    const [id, entry] = Object.entries(enemyNamesEN)[0] as [string, { name: string }]
    renderWithProviders(
      <BattleFilterDropdown
        family="enemy"
        selected={new Set([id])}
        onSelectionChange={vi.fn()}
        placeholderKey="keyword.filterIdentity"
      />,
    )

    const options = await screen.findAllByTestId('option')
    expect(options.find((option) => option.dataset.value === id)).toHaveTextContent(entry.name)
    expect(screen.getByTestId('selected-count')).toHaveTextContent('1')
  })

  it('labels every sinner event option apart by its event name', async () => {
    await i18n.changeLanguage('KR')
    renderWithProviders(
      <BattleFilterDropdown
        family="sinnerEvent"
        selected={new Set()}
        onSelectionChange={vi.fn()}
        placeholderKey="keyword.filterSinnerEvent"
      />,
    )

    const options = await screen.findAllByTestId('option')
    const labels = options.map((option) => option.textContent)
    expect(new Set(labels).size).toBe(labels.length)
    const labelOf = (id: string) =>
      options.find((option) => option.dataset.value === id)?.textContent
    expect(labelOf('10101')).toBe('이상 (한봄 밤의 꿈 2)')
    expect(labelOf('400025')).toBe('이상 (한봄 밤의 꿈 3)')
  })
  it('labels every envy copy apart by its identity title', async () => {
    await i18n.changeLanguage('KR')
    renderWithProviders(
      <BattleFilterDropdown
        family="enemy"
        selected={new Set()}
        onSelectionChange={vi.fn()}
        placeholderKey="keyword.filterIdentity"
      />,
    )

    const options = await screen.findAllByTestId('option')
    const labelOf = (id: string) =>
      options.find((option) => option.dataset.value === id)?.textContent
    expect(labelOf('1110')).toBe('질투 죄종 - 남부 리우 협회 4과')
    expect(labelOf('1111')).toBe('질투 죄종 - 남부 리우 협회 6과')
    expect(labelOf('1112')).toBe('질투 죄종 - 남부 리우 협회 5과')
  })
})
