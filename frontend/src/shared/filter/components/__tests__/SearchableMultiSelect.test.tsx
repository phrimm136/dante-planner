import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SearchableMultiSelect } from '../SearchableMultiSelect'

const OPTIONS = [
  { value: '10101', label: 'Yi Sang' },
  { value: '400025', label: 'Yi Sang' },
  { value: '10201', label: 'Faust' },
]

function renderSelect(onSelectionChange = vi.fn(), selected = new Set<string>()) {
  render(
    <SearchableMultiSelect
      options={OPTIONS}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder="Sinners"
      searchPlaceholder="Search"
      sortByLabel={false}
    />,
  )
  return onSelectionChange
}

async function openSelect() {
  const user = userEvent.setup()
  await user.click(screen.getByRole('combobox'))
  return user
}

const yiSangOptions = () => screen.getAllByRole('option', { name: 'Yi Sang' })

describe('SearchableMultiSelect with repeated labels', () => {
  it('highlights each same-label option on its own', async () => {
    renderSelect()
    const user = await openSelect()
    const [first, second] = yiSangOptions()
    if (first === undefined || second === undefined) throw new Error('two Yi Sang options expected')

    await user.hover(first)
    expect(first).toHaveAttribute('aria-selected', 'true')
    expect(second).toHaveAttribute('aria-selected', 'false')

    await user.hover(second)
    expect(first).toHaveAttribute('aria-selected', 'false')
    expect(second).toHaveAttribute('aria-selected', 'true')
  })

  it('selects each same-label option by its own id', async () => {
    const onSelectionChange = renderSelect()
    const user = await openSelect()
    const [first, second] = yiSangOptions()
    if (first === undefined || second === undefined) throw new Error('two Yi Sang options expected')

    await user.click(first)
    expect(onSelectionChange).toHaveBeenLastCalledWith(new Set(['10101']))

    await user.click(second)
    expect(onSelectionChange).toHaveBeenLastCalledWith(new Set(['400025']))
  })

  it('finds both same-label options by label and none by id', async () => {
    renderSelect()
    const user = await openSelect()

    await user.type(screen.getByPlaceholderText('Search'), 'yi')
    expect(yiSangOptions()).toHaveLength(2)
    expect(screen.queryByRole('option', { name: 'Faust' })).toBeNull()

    await user.clear(screen.getByPlaceholderText('Search'))
    await user.type(screen.getByPlaceholderText('Search'), '10101')
    expect(screen.queryAllByRole('option')).toHaveLength(0)
  })
})
