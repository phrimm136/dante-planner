import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'

import { DUNGEON_IDX, DIFFICULTY_LABELS } from '@/shared/gameData'
import type { ThemePackDetail } from '@/pages/themePack'
import { FloorDisplay } from '../ThemePackDetailPage'

type Conditions = ThemePackDetail['exceptionConditions']

/**
 * Pull the floor badges that live under a given difficulty header.
 * The header is a sibling of the badge container, so we walk up to the
 * group div (the keyed wrapper) and read all <span> badges inside it.
 */
function badgesFor(label: string): string[] {
  const header = screen.getByText(label)
  const group = header.parentElement
  if (!group) throw new Error(`No group wrapper for "${label}"`)
  return within(group)
    .getAllByText(/^\d+F$/)
    .map((el) => el.textContent ?? '')
}

describe('FloorDisplay', () => {
  it('renders 1F-5F for Normal when selectableFloors covers all indices', () => {
    const conditions: Conditions = [
      { dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [0, 1, 2, 3, 4] },
    ]
    render(<FloorDisplay conditions={conditions} />)
    expect(badgesFor(DIFFICULTY_LABELS.NORMAL)).toEqual(['1F', '2F', '3F', '4F', '5F'])
  })

  it('renders only the selectable Hard floors and sorts them numerically', () => {
    const conditions: Conditions = [{ dungeonIdx: DUNGEON_IDX.HARD, selectableFloors: [4, 3] }]
    render(<FloorDisplay conditions={conditions} />)
    expect(badgesFor(DIFFICULTY_LABELS.HARD)).toEqual(['4F', '5F'])
  })

  it('synthesizes 6F-10F for Infinity when selectableFloors is absent', () => {
    const conditions: Conditions = [{ dungeonIdx: DUNGEON_IDX.PARALLEL }]
    render(<FloorDisplay conditions={conditions} />)
    expect(badgesFor(DIFFICULTY_LABELS.INFINITY_MIRROR)).toEqual(['6F', '7F', '8F', '9F', '10F'])
  })

  it('synthesizes 11F-15F for Extreme when selectableFloors is absent', () => {
    const conditions: Conditions = [{ dungeonIdx: DUNGEON_IDX.EXTREME }]
    render(<FloorDisplay conditions={conditions} />)
    expect(badgesFor(DIFFICULTY_LABELS.EXTREME_MIRROR)).toEqual(['11F', '12F', '13F', '14F', '15F'])
  })

  it('renders multiple difficulty groups together in canonical order', () => {
    const conditions: Conditions = [
      { dungeonIdx: DUNGEON_IDX.HARD, selectableFloors: [4] },
      { dungeonIdx: DUNGEON_IDX.PARALLEL },
      { dungeonIdx: DUNGEON_IDX.EXTREME },
    ]
    const { container } = render(<FloorDisplay conditions={conditions} />)

    const headers = Array.from(container.querySelectorAll('.text-xs.font-medium')).map(
      (el) => el.textContent,
    )
    expect(headers).toEqual([
      DIFFICULTY_LABELS.HARD,
      DIFFICULTY_LABELS.INFINITY_MIRROR,
      DIFFICULTY_LABELS.EXTREME_MIRROR,
    ])
    expect(badgesFor(DIFFICULTY_LABELS.HARD)).toEqual(['5F'])
    expect(badgesFor(DIFFICULTY_LABELS.INFINITY_MIRROR)).toEqual(['6F', '7F', '8F', '9F', '10F'])
    expect(badgesFor(DIFFICULTY_LABELS.EXTREME_MIRROR)).toEqual(['11F', '12F', '13F', '14F', '15F'])
  })

  it('renders nothing when no conditions are present', () => {
    const { container } = render(<FloorDisplay conditions={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('prefers data-supplied selectableFloors over the synthesized range when both could apply', () => {
    // Defensive: should Infinity ever ship per-pack selectableFloors, those
    // win over the DUNGEON_FIXED_FLOOR_RANGE fallback.
    const conditions: Conditions = [{ dungeonIdx: DUNGEON_IDX.PARALLEL, selectableFloors: [0, 1] }]
    render(<FloorDisplay conditions={conditions} />)
    expect(badgesFor(DIFFICULTY_LABELS.INFINITY_MIRROR)).toEqual(['1F', '2F'])
  })

  it('deduplicates floors across multiple conditions for the same difficulty', () => {
    const conditions: Conditions = [
      { dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [0, 1] },
      { dungeonIdx: DUNGEON_IDX.NORMAL, selectableFloors: [1, 2] },
    ]
    render(<FloorDisplay conditions={conditions} />)
    expect(badgesFor(DIFFICULTY_LABELS.NORMAL)).toEqual(['1F', '2F', '3F'])
  })
})
