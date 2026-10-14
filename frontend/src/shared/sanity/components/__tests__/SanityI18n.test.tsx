import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import panicInfoEN from '@static/i18n/EN/panicInfo.json'
import enTable from '@static/data/fontAdvances/EN.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { CLIENT_TEXT_SCALE, SANITY_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import { PanicTypeSection, PanicTypeSectionI18n, SanityConditionsSectionI18n } from '../SanityI18n'

const LABELS: Record<string, string> = {
  'sanity.lowMoraleEffect': 'Low Morale Effects',
  'sanity.panicEffect': 'Panic Effects',
  'sanity.panicType': 'Panic Type',
}

vi.mock(import('react-i18next'), async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useTranslation: () =>
      ({
        t: (key: string, fallback?: string) => LABELS[key] ?? fallback ?? key,
        i18n: { language: 'EN' },
      }) as unknown as ReturnType<typeof actual.useTranslation>,
  }
})

const EN_TABLE = FontAdvanceTableSchema.parse(enTable)

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => EN_TABLE,
}))

vi.mock(import('@/shared/gameText'), async (importOriginal) => ({
  ...(await importOriginal()),
  FormattedDescription: ({ text }: { text: string }) => <span>{text}</span>,
}))

vi.mock(import('../../hooks/usePanicInfo'), async (importOriginal) => ({
  ...(await importOriginal()),
  usePanicInfo: () => ({
    data: {
      ...panicInfoEN,
      '8888': {
        name: 'Tremor',
        lowMoraleDesc: '',
        panicDesc: 'Does not act.',
        flavor: 'My hands shake...',
      },
    },
  }),
}))

vi.mock('../../hooks/useSanityConditionFormatter', () => ({
  useSanityConditionFormatter: () => ({ formatAll: (names: string[]) => names }),
}))

const slot = (container: HTMLElement, name: string): HTMLElement => {
  const found = container.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (found === null) throw new Error(`missing [data-slot="${name}"]`)
  return found
}

function flatten(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
  if (node.nodeName === 'BR') return '|'
  return Array.from(node.childNodes).map(flatten).join('')
}

function renderPanic(lowMorale?: string, panicType = '9999', flavor?: string) {
  return render(
    <PanicTypeSection
      panicType={panicType}
      nameSlot={<span>Panic</span>}
      descSlot={<span>Does not act.</span>}
      {...(lowMorale !== undefined && { lowMoraleSlot: <span>{lowMorale}</span> })}
      flavor={flavor}
    />,
  )
}

const FLAVOR_KR = '손이 떨리면, 가위질도 흔들려… 이대로는 재단할 수 없어…'

describe('panic section', () => {
  it('renders both parts as one block with no margin between them', () => {
    const { container } = renderPanic('Lose 3 SP.')
    const desc = slot(container, 'panic-desc')
    expect(flatten(desc)).toBe('·Low Morale Effects|Lose 3 SP.|·Panic Effects|Does not act.')
    expect(desc.querySelectorAll('[class*="mt-"]')).toHaveLength(0)
    expect(slot(container, 'low-morale-effect').textContent).toBe('·Low Morale EffectsLose 3 SP.')
  })

  it('renders only the panic part without low-morale text', () => {
    const { container } = renderPanic()
    expect(flatten(slot(container, 'panic-desc'))).toBe('·Panic Effects|Does not act.')
    expect(container.querySelector('[data-slot="low-morale-effect"]')).toBeNull()
  })

  it('places the column pieces absolutely and the description in flow', () => {
    const { container } = renderPanic()
    const section = slot(container, 'panic-section')
    expect(section.style.position).toBe('relative')
    expect(section.style.minHeight).toBe('9.8438rem')
    expect(section.style.marginBottom).toBe('1.0938rem')

    const tag = slot(container, 'panic-tag')
    expect(tag.style.position).toBe('absolute')
    expect(tag.style.fontSize).toBe('0.9844rem')
    expect(tag.style.whiteSpace).toBe('nowrap')
    expect(slot(container, 'panic-tag-bg').style.width).toBe('6.0156rem')

    const icon = slot(container, 'panic-icon')
    expect(icon.style.left).toBe('1.0544rem')
    expect(icon.classList).toContain('object-contain')

    const name = slot(container, 'panic-name')
    expect(name.classList).toContain('text-center')
    expect(name.textContent).toBe('Panic')

    const desc = slot(container, 'panic-desc')
    expect(desc.style.marginLeft).toBe('6.5625rem')
    expect(desc.style.paddingTop).toBe('2.5187rem')
    expect(desc.style.top).toBe('0.3719rem')
  })

  it('draws the panic tag label in the regular weight at the top of its band', async () => {
    const { container } = renderPanic()
    const text = await screen.findByText('Panic Type', { selector: '[data-slot="panic-tag-text"]' })
    expect(slot(container, 'panic-tag').classList).not.toContain('font-bold')
    expect(text.style.fontSize).toBe(clientLength(SANITY_UNITS.PANIC.TAG_TEXT.FONT))
    expect(text.style.letterSpacing).toBe(
      `${String(SANITY_UNITS.PANIC.TAG_TEXT.LETTER_SPACING_EM + EN_TABLE.normalSpacingOffset)}em`,
    )
    expect(text.style.whiteSpace).toBe('nowrap')
  })

  it('shrinks a panic tag label that overruns the tag instead of wrapping it', async () => {
    const shipped = LABELS['sanity.panicType']
    LABELS['sanity.panicType'] = 'Panic Type Label'
    try {
      renderPanic()
      const text = await screen.findByText('Panic Type Label', {
        selector: '[data-slot="panic-tag-text"]',
      })
      const { FONT, MIN_FONT } = SANITY_UNITS.PANIC.TAG_TEXT
      const units =
        (Number.parseFloat(text.style.fontSize) / CLIENT_TEXT_SCALE.FONT_REM) *
        CLIENT_TEXT_SCALE.FONT_UNITS
      expect(units).toBeLessThan(FONT)
      expect(units).toBeGreaterThanOrEqual(MIN_FONT)
    } finally {
      LABELS['sanity.panicType'] = shipped ?? ''
    }
  })

  it('renders only the panic description, with no labels, for panic type 9997', () => {
    const { container } = renderPanic('Lose 3 SP.', '9997')
    expect(flatten(slot(container, 'panic-desc'))).toBe('Does not act.')
    expect(container.querySelector('[data-slot="low-morale-effect"]')).toBeNull()
  })

  it('appends the flavor after an empty line in small italic brown', () => {
    const { container } = renderPanic('Lose 3 SP.', '9999', FLAVOR_KR)
    const desc = slot(container, 'panic-desc')
    expect(flatten(desc)).toBe(
      `·Low Morale Effects|Lose 3 SP.|·Panic Effects|Does not act.||${FLAVOR_KR}`,
    )
    const flavor = slot(desc, 'panic-flavor')
    expect(flavor.textContent).toBe(FLAVOR_KR)
    expect(flavor.style.fontSize).toBe('0.7219rem')
    expect(flavor.style.fontStyle).toBe('italic')
    expect(flavor.style.color).toBe('#724b2b')
    expect(flavor.style.whiteSpace).toBe('pre-line')
    expect(desc.lastElementChild).toBe(flavor)
  })

  it('appends the flavor to a description-only panic type', () => {
    const { container } = renderPanic(undefined, '9997', FLAVOR_KR)
    expect(flatten(slot(container, 'panic-desc'))).toBe(`Does not act.||${FLAVOR_KR}`)
  })

  it.each([undefined, ''])('adds no flavor and no line breaks for flavor %j', (flavor) => {
    const { container } = renderPanic(undefined, '9999', flavor)
    expect(flatten(slot(container, 'panic-desc'))).toBe('·Panic Effects|Does not act.')
    expect(container.querySelector('[data-slot="panic-flavor"]')).toBeNull()
  })

  it('draws no runtime panic-buff row', () => {
    const { container } = renderPanic('Lose 3 SP.')
    expect(container.querySelector('[data-slot="panic-buff"]')).toBeNull()
  })
})

describe('panic section from panic info', () => {
  it('shows the low-morale text of an identity panic type before its panic text', async () => {
    const { container } = render(<PanicTypeSectionI18n panicType="1001" />)
    await screen.findByText('Compulsion')
    expect(flatten(slot(container, 'panic-desc'))).toBe(
      '·Low Morale Effects|Turn Start: Gain 2 [Agility] and 2 [Vulnerable]|·Panic Effects|Turn Start: Lose 10 HP.',
    )
  })

  it('shows the flavor of a panic info entry after its panic text', async () => {
    const { container } = render(<PanicTypeSectionI18n panicType="8888" />)
    await screen.findByText('Tremor')
    expect(flatten(slot(container, 'panic-desc'))).toBe(
      '·Panic Effects|Does not act.||My hands shake...',
    )
    expect(slot(container, 'panic-flavor').textContent).toBe('My hands shake...')
  })

  it('shows only [Unobservable] for panic type 9997', async () => {
    const { container } = render(<PanicTypeSectionI18n panicType="9997" />)
    await screen.findByText('???')
    expect(flatten(slot(container, 'panic-desc'))).toBe('Unobservable')
  })

  it('shows no low-morale part when the entry has no low-morale text', async () => {
    const { container } = render(<PanicTypeSectionI18n panicType="9999" />)
    await screen.findByText('Panic Effects', { exact: false })
    expect(container.querySelector('[data-slot="low-morale-effect"]')).toBeNull()
  })
})

describe('condition sections', () => {
  const renderConditions = (add: string[], min: string[]) =>
    render(<SanityConditionsSectionI18n addConditions={add} minConditions={min} />)

  const section = (container: HTMLElement, kind: 'add' | 'min'): HTMLElement => {
    const found = container.querySelector<HTMLElement>(
      `[data-slot="sanity-condition"][data-kind="${kind}"]`,
    )
    if (found === null) throw new Error(`missing ${kind} section`)
    return found
  }

  it.each([
    ['add', '1.6406rem'],
    ['min', '1.6187rem'],
  ] as const)('clamps the %s tag and keeps its height %s', (kind, height) => {
    const { container } = renderConditions(['a'], ['b'])
    const tag = slot(section(container, kind), 'sanity-condition-tag')
    expect(tag.style.height).toBe(height)
    expect(tag.style.minWidth).toBe('8.8594rem')
    expect(tag.style.maxWidth).toBe('12.5781rem')
    expect(tag.style.paddingInline).toBe('0.5469rem')
    expect(slot(tag, 'sanity-condition-tag-text').style.fontSize).toBe('0.875rem')
    expect(tag.style.whiteSpace).toBe('')
  })

  it('separates condition items by the item gap', () => {
    const { container } = renderConditions(['first', 'second'], ['third'])
    const list = slot(section(container, 'add'), 'sanity-condition-list')
    expect(list.style.rowGap).toBe('0.7313rem')
    expect(list.style.paddingTop).toBe('2.0812rem')
    expect(list.children).toHaveLength(2)
    expect(list.textContent).toBe('·first·second')
  })

  it.each([
    ['add', '1.0938rem', '6.2772rem'],
    ['min', '1.0933rem', '6.2781rem'],
  ] as const)('draws the %s icon under the text', (kind, right, height) => {
    const { container } = renderConditions(['a'], ['b'])
    const box = section(container, kind)
    const icon = slot(box, 'sanity-condition-icon')
    expect(box.classList).toContain('isolate')
    expect(box.style.minHeight).toBe('6.5625rem')
    expect(box.style.marginBottom).toBe('1.0938rem')
    expect(icon.classList).toContain('-z-10')
    expect(icon.classList).toContain('object-contain')
    expect(icon.style.right).toBe(right)
    expect(icon.style.height).toBe(height)
    expect(icon.style.width).toBe('6.125rem')
    expect(icon.style.opacity).toBe('0.275')
  })

  it('keeps the empty placeholder when a kind has no conditions', () => {
    const { container } = renderConditions([], ['b'])
    expect(slot(section(container, 'add'), 'sanity-condition-list').textContent).toBe(
      'No sanity increase conditions',
    )
    expect(section(container, 'min').textContent).toContain('·b')
  })
})

describe('sanity text rendering', () => {
  it('marks the panic section root as game text', () => {
    const { container } = renderPanic()

    expect(slot(container, 'panic-section')).toHaveClass('game-text')
  })

  it('marks each condition section root as game text', () => {
    const { container } = render(
      <SanityConditionsSectionI18n addConditions={['a']} minConditions={[]} />,
    )
    const sections = container.querySelectorAll('[data-slot="sanity-condition"]')

    expect(sections).toHaveLength(2)
    for (const section of sections) expect(section).toHaveClass('game-text')
  })
})
