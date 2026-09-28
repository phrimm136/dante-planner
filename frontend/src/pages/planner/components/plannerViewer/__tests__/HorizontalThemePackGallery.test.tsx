import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HorizontalThemePackGallery } from '../HorizontalThemePackGallery'
import { buildFloorSelection } from '@/test-utils'
import { ThemePackIdSchema } from '@/shared/gameData'

vi.mock('@/pages/themePack/hooks/useThemePackListData', () => ({
  useThemePackListSpec: () =>
    Object.fromEntries(Array.from({ length: 15 }, (_, i) => [String(1001 + i), {}])),
  useThemePackListI18n: () => ({}),
}))

vi.mock('../ThemePackTrackerCard', () => ({
  ThemePackTrackerCard: ({ packId, floorNumber }: { packId: string; floorNumber: number }) => (
    <div data-testid={`pack-card-${packId}`} data-floor={String(floorNumber)} />
  ),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: { number?: number }) =>
        key === 'pages.plannerMD.floor' ? `Floor ${options?.number}` : key,
      i18n: { language: 'EN' },
    }),
  }
})

const floorsWithPacks = (packIds: readonly string[]) =>
  packIds.map((packId) => buildFloorSelection({ themePackId: ThemePackIdSchema.parse(packId) }))

const renderGallery = (packIds: readonly string[], floorCount: number) =>
  render(
    <HorizontalThemePackGallery
      floorSelections={floorsWithPacks(packIds)}
      floorCount={floorCount}
      sectionNotes={{}}
      doneMarks={{}}
      onTogglePackDone={vi.fn()}
      focusedThemePackId={null}
      onFocusToggle={vi.fn()}
      onHoverChange={vi.fn()}
    />,
  )

const shownFloors = () =>
  screen.getAllByTestId(/^pack-card-/).map((card) => card.getAttribute('data-floor'))

describe('HorizontalThemePackGallery', () => {
  const fifteenPacks = Array.from({ length: 15 }, (_, i) => String(1001 + i))

  it('shows only the floors below the count of a 15-floor planner switched to 5F', () => {
    renderGallery(fifteenPacks, 5)

    expect(shownFloors()).toEqual(['1', '2', '3', '4', '5'])
    expect(screen.queryByText('Floor 6')).toBeNull()
  })

  it('places a pack repeated on a hidden floor at its floor below the count', () => {
    const repeated = fifteenPacks.map((packId, i) => (i === 6 ? '1002' : packId))
    renderGallery(repeated, 5)

    expect(screen.getAllByTestId('pack-card-1002').map((card) => card.dataset.floor)).toEqual(['2'])
  })

  it('shows the floors a planner stores when it holds fewer than its count', () => {
    renderGallery(fifteenPacks.slice(0, 3), 5)

    expect(shownFloors()).toEqual(['1', '2', '3'])
  })
})
