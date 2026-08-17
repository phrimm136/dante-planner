/**
 * Unit keyword options render Unity rich text the same way the reference
 * parser does, and the searchable label keeps only the plain text.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Fragment, type ReactNode } from 'react'

import { ASSOCIATIONS } from '@/shared/gameData'
import { UnitKeywordDropdown } from '../UnitKeywordDropdown'

const RICH_LABEL = '<color=#d40000><s>Jia Family</s></color>'
const SIZED_LABEL = 'サ<size=50%>ル</size>党派'

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

function referenceStrikethrough(text: string): ReactNode {
  if (!text.includes('<s>')) return text

  const nodes: ReactNode[] = []
  let lastIndex = 0
  let key = 0

  for (const match of text.matchAll(/<s>([\s\S]*?)<\/s>/g)) {
    if (match.index > lastIndex) nodes.push(text.slice(lastIndex, match.index))
    nodes.push(<s key={key++}>{match[1]}</s>)
    lastIndex = match.index + match[0].length
  }

  if (nodes.length === 0) return text
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex))
  return <Fragment>{nodes}</Fragment>
}

function referenceLabel(label: string): ReactNode {
  const colorMatch = label.match(/<color=([^>]+)>/)
  if (!colorMatch) return referenceStrikethrough(label)

  const color = colorMatch[1]
  const text = label.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  return <span style={{ color }}>{referenceStrikethrough(text)}</span>
}

function htmlOf(node: ReactNode): string {
  const { container } = render(<span>{node}</span>)
  return (container.firstChild as HTMLElement).innerHTML
}

describe('UnitKeywordDropdown rich text', () => {
  it('renders colored strikethrough labels like the reference parser', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown selected={new Set()} onSelectionChange={vi.fn()} />)

    await user.click(screen.getByRole('combobox'))

    const optionHtml = screen.getAllByRole('option').map((option) => option.innerHTML)

    expect(
      optionHtml.some((html) =>
        html.includes(`<span>${htmlOf(referenceLabel(RICH_LABEL))}</span>`),
      ),
    ).toBe(true)
    expect(
      optionHtml.some((html) =>
        html.includes(`<span>${htmlOf(referenceLabel(SIZED_LABEL))}</span>`),
      ),
    ).toBe(true)
  })

  it('searches on the tag-free label', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown selected={new Set()} onSelectionChange={vi.fn()} />)

    await user.click(screen.getByRole('combobox'))
    await user.type(screen.getByPlaceholderText('Search Unit Keywords...'), 'Jia Family')

    expect(screen.getAllByRole('option')).toHaveLength(1)
  })
})
