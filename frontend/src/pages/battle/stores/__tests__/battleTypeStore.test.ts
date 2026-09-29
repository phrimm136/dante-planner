import { describe, it, expect, beforeEach } from 'vitest'

import { battleTypeStore, setBattleMode, setBattleType } from '../battleTypeStore'
import { enemyFilterStore } from '../enemyFilterStore'

beforeEach(() => {
  enemyFilterStore.resetAll()
  battleTypeStore.setState(battleTypeStore.getInitialState())
})

describe('setBattleMode', () => {
  it('clears the enemy drill-downs and keeps the search and type', () => {
    setBattleType('assistant')
    enemyFilterStore.setters.selectedChapters(new Set(['canto:8']))
    enemyFilterStore.setSearchQuery('Zilu')

    setBattleMode('mirrorDungeon')

    const { values, searchQuery } = enemyFilterStore.store.getState()
    expect(battleTypeStore.getState()).toEqual({ mode: 'mirrorDungeon', entityType: 'assistant' })
    expect(values.selectedChapters.size).toBe(0)
    expect(searchQuery).toBe('Zilu')
  })

  it('keeps the drill-downs when the selected mode is pressed again', () => {
    enemyFilterStore.setters.selectedChapters(new Set(['canto:8']))

    setBattleMode('story')

    expect([...enemyFilterStore.store.getState().values.selectedChapters]).toEqual(['canto:8'])
  })
})
