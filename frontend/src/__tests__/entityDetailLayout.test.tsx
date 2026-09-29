import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'

import enDatabase from '@static/i18n/EN/database.json'
import identity10310 from '@static/data/identity/10310.json'
import identityI18n10310 from '@static/i18n/EN/identity/10310.json'
import ego20102 from '@static/data/ego/20102.json'
import egoI18n20102 from '@static/i18n/EN/ego/20102.json'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import i18n from '@/lib/i18n'
import { EGODataSchema } from '@/pages/ego'
import { IdentityDataSchema } from '@/pages/identity'
import { mountRoute } from '@/test-utils/mountRoute'
import enemy1280 from '@static/data/enemy/1280.json'
import enemyI18n1280 from '@static/i18n/EN/enemy/1280.json'
import assistant400001 from '@static/data/assistant/400001.json'
import assistantI18n400001 from '@static/i18n/EN/assistant/400001.json'
import sinnerEvent10101 from '@static/data/sinnerEvent/10101.json'
import sinnerEventI18n10101 from '@static/i18n/EN/sinnerEvent/10101.json'
import { AssistantDataSchema, EnemyDataSchema, SinnerEventDataSchema } from '@/pages/battle'

vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: vi.fn(() => false),
}))

type PageModule = { default: () => ReactNode }

type SectionKey = keyof typeof enDatabase.section

interface EntityLayoutRow {
  entity: string
  path: string
  url: string
  load: () => Promise<PageModule>
  name: string
  hasEgo: boolean
  hasSanity: boolean
  selectorButton: RegExp
  recordSkillIds: readonly string[]
  skillOrder: readonly string[]
}

const ENTITY_ROWS: readonly EntityLayoutRow[] = [
  {
    entity: 'identity 10310',
    path: '/identity/$id',
    url: '/identity/10310',
    load: () => import('@/pages/identity/IdentityDetailPage'),
    name: identityI18n10310.name,
    hasEgo: false,
    hasSanity: true,
    selectorButton: /tier 1/i,
    recordSkillIds: Object.keys(IdentityDataSchema.parse(identity10310).skills),
    skillOrder: [
      '1031001',
      '1031005',
      '1031002',
      '1031006',
      '1031003',
      '1031007',
      '1031004',
      '1031008',
    ],
  },
  {
    entity: 'E.G.O 20102',
    path: '/ego/$id',
    url: '/ego/20102',
    load: () => import('@/pages/ego/EGODetailPage'),
    name: egoI18n20102.name,
    hasEgo: false,
    hasSanity: false,
    selectorButton: /tier 1/i,
    recordSkillIds: Object.keys(EGODataSchema.parse(ego20102).skills),
    skillOrder: ['2010211', '2010221'],
  },
  {
    entity: 'enemy 1280',
    path: '/battle/enemy/$id',
    url: '/battle/enemy/1280',
    load: () => import('@/pages/battle/EnemyDetailPage'),
    name: enemyI18n1280.name,
    hasEgo: false,
    hasSanity: false,
    selectorButton: /^Lv\. 50$/,
    recordSkillIds: Object.keys(EnemyDataSchema.parse(enemy1280).phases[0].skills),
    skillOrder: ['806301', '806302', '806303', '806304', '806305', '806306', '806307'],
  },
  {
    entity: 'assistant 400001',
    path: '/battle/assistant/$id',
    url: '/battle/assistant/400001',
    load: () => import('@/pages/battle/AssistantDetailPage'),
    name: assistantI18n400001.name,
    hasEgo: false,
    hasSanity: true,
    selectorButton: /^Lv\. 50$/,
    recordSkillIds: Object.keys(AssistantDataSchema.parse(assistant400001).phases[0].skills),
    skillOrder: ['40000101', '40000102', '40000103', '40000104'],
  },
  {
    entity: 'event sinner 10101',
    path: '/battle/sinnerEvent/$id',
    url: '/battle/sinnerEvent/10101',
    load: () => import('@/pages/battle/SinnerEventDetailPage'),
    name: sinnerEventI18n10101.name,
    hasEgo: false,
    hasSanity: true,
    selectorButton: /^Lv\. 50$/,
    recordSkillIds: Object.keys(SinnerEventDataSchema.parse(sinnerEvent10101).phases[0].skills),
    skillOrder: ['1010101', '1010102', '1010103', '1010104'],
  },
]

const TAB_LABELS = [
  enDatabase.skill.skill1,
  enDatabase.skill.skill2,
  enDatabase.skill.skill3,
  enDatabase.skill.defense,
  enDatabase.skill.awakening,
  enDatabase.skill.corrosion,
  enDatabase.tabs.skills,
]

const LOAD_WAIT_MS = 5000
const TEST_BUDGET_MS = 20000

function expectedSectionTitles(row: EntityLayoutRow): string[] {
  const keys: SectionKey[] = [
    'skills',
    'passives',
    ...(row.hasEgo ? (['ego'] as const) : []),
    ...(row.hasSanity ? (['sanity'] as const) : []),
  ]
  return keys.map((key) => enDatabase.section[key])
}

function slot(name: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-slot="${name}"]`)
}

function requireSlot(name: string): HTMLElement {
  const element = slot(name)
  if (element === null) throw new Error(`no ${name} slot rendered`)
  return element
}

function sectionTitles(): string[] {
  return within(requireSlot('detail-sections'))
    .queryAllByRole('region')
    .map((section) => within(section).getByRole('heading', { level: 2 }).textContent ?? '')
}

function renderedSkillIds(): string[] {
  return [...document.querySelectorAll('[data-skill-id]')].map(
    (card) => card.getAttribute('data-skill-id') ?? '',
  )
}

function precedes(first: HTMLElement, second: HTMLElement): boolean {
  return (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
}

async function mountSettled(row: EntityLayoutRow) {
  mountRoute(row.path, row.url, (await row.load()).default)
  const titles = expectedSectionTitles(row)
  await waitFor(() => expect(sectionTitles()).toEqual(titles), { timeout: LOAD_WAIT_MS })
  await waitFor(() => expect(document.querySelectorAll('[data-slot="skeleton"]').length).toBe(0), {
    timeout: LOAD_WAIT_MS,
  })
}

function expectHeaderBlock(row: EntityLayoutRow) {
  const header = requireSlot('detail-header')
  expect(within(header).getByRole('heading', { level: 1 }).textContent).toBe(row.name)
  expect(within(header).getByRole('img', { name: row.name })).toBeDefined()
}

function tabControls() {
  const buttonNames = screen.queryAllByRole('button').map((button) => button.textContent?.trim())
  return {
    tablists: screen.queryAllByRole('tablist').length,
    tabs: screen.queryAllByRole('tab').length,
    tabButtons: TAB_LABELS.filter((label) => buttonNames.includes(label)),
  }
}

beforeEach(async () => {
  vi.mocked(useIsBreakpoint).mockReturnValue(false)
  await i18n.changeLanguage('EN')
})

describe.each(ENTITY_ROWS)('$entity detail layout', (row) => {
  describe('desktop (lg and up)', () => {
    it(
      'holds the name block and artwork beside the metadata panels in the header row',
      async () => {
        await mountSettled(row)

        const headerRow = requireSlot('detail-header-row')
        expect([...headerRow.children]).toEqual([
          requireSlot('detail-header'),
          requireSlot('detail-metadata'),
        ])
        expectHeaderBlock(row)
        expect(
          within(requireSlot('detail-metadata')).queryAllByRole('heading', { level: 1 }),
        ).toHaveLength(0)
      },
      TEST_BUDGET_MS,
    )

    it(
      'places the full-width selector row below the header row and above the sections',
      async () => {
        await mountSettled(row)

        const selectorRow = requireSlot('detail-selector')
        expect(requireSlot('detail-header-row').nextElementSibling).toBe(selectorRow)
        expect(selectorRow.nextElementSibling).toBe(requireSlot('detail-sections'))
        expect(
          within(selectorRow).getAllByRole('button', { name: row.selectorButton }),
        ).toHaveLength(1)
      },
      TEST_BUDGET_MS,
    )

    it(
      'stacks the sections under localized titles in the order Skills, Passives, E.G.O, Sanity',
      async () => {
        await mountSettled(row)

        expect(sectionTitles()).toEqual(expectedSectionTitles(row))
        expect(sectionTitles().includes(enDatabase.section.ego)).toBe(row.hasEgo)
        expect(sectionTitles().includes(enDatabase.section.sanity)).toBe(row.hasSanity)
      },
      TEST_BUDGET_MS,
    )

    it(
      'renders no skill-slot, awaken/erosion or section tab control',
      async () => {
        await mountSettled(row)

        expect(tabControls()).toEqual({ tablists: 0, tabs: 0, tabButtons: [] })
      },
      TEST_BUDGET_MS,
    )

    it(
      'renders every skill of the entity at once, in the row order',
      async () => {
        await mountSettled(row)

        expect([...renderedSkillIds()].sort()).toEqual([...row.recordSkillIds].sort())
        expect(renderedSkillIds()).toEqual(row.skillOrder)
      },
      TEST_BUDGET_MS,
    )
  })

  describe('mobile (below lg)', () => {
    beforeEach(() => {
      vi.mocked(useIsBreakpoint).mockReturnValue(true)
    })

    it(
      'stacks header, metadata, selector and sections in that order',
      async () => {
        await mountSettled(row)

        expect(slot('detail-header-row')).toBeNull()
        const stack = [
          'detail-header',
          'detail-metadata',
          'detail-selector',
          'detail-sections',
        ].map(requireSlot)
        for (const [index, element] of stack.slice(1).entries()) {
          const previous = stack[index]
          if (previous === undefined) throw new Error('stack out of range')
          expect(precedes(previous, element)).toBe(true)
        }
        expectHeaderBlock(row)
      },
      TEST_BUDGET_MS,
    )

    it(
      'shows the same sections under the same titles',
      async () => {
        await mountSettled(row)

        expect(sectionTitles()).toEqual(expectedSectionTitles(row))
      },
      TEST_BUDGET_MS,
    )

    it(
      'renders no skills/passives/sanity tab control',
      async () => {
        await mountSettled(row)

        expect(tabControls()).toEqual({ tablists: 0, tabs: 0, tabButtons: [] })
      },
      TEST_BUDGET_MS,
    )

    it(
      'renders every skill of the entity at once, in the row order',
      async () => {
        await mountSettled(row)

        expect([...renderedSkillIds()].sort()).toEqual([...row.recordSkillIds].sort())
        expect(renderedSkillIds()).toEqual(row.skillOrder)
      },
      TEST_BUDGET_MS,
    )
  })
})
