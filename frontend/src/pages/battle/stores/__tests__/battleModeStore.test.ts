import { describe, it, expect, beforeEach } from 'vitest'

import { battleFilterStore } from '../battleFilterStore'
import { battleModeStore, setBattleMode } from '../battleModeStore'

beforeEach(() => {
  battleFilterStore.resetAll()
  battleModeStore.setState(battleModeStore.getInitialState())
})

describe('setBattleMode', () => {
  it('clears the drill-downs and keeps the type filter and the search', () => {
    battleFilterStore.setters.selectedTypes(new Set(['assistant']))
    battleFilterStore.setters.selectedChapters(new Set(['canto:8']))
    battleFilterStore.setSearchQuery('Zilu')

    setBattleMode('mirrorDungeon')

    const { values, searchQuery } = battleFilterStore.store.getState()
    expect(battleModeStore.getState().mode).toBe('mirrorDungeon')
    expect(values.selectedChapters.size).toBe(0)
    expect([...values.selectedTypes]).toEqual(['assistant'])
    expect(searchQuery).toBe('Zilu')
  })

  it('keeps the drill-downs when the selected mode is pressed again', () => {
    battleFilterStore.setters.selectedChapters(new Set(['canto:8']))

    setBattleMode('story')

    expect([...battleFilterStore.store.getState().values.selectedChapters]).toEqual(['canto:8'])
  })
})
