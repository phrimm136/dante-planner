import { describe, it, expect, beforeEach } from 'vitest'

import type { BattleCase } from '../../lib/battleVocabulary'
import { enemyFilterStore, setEnemyCases } from '../enemyFilterStore'

beforeEach(() => {
  enemyFilterStore.resetAll()
})

describe('setEnemyCases', () => {
  it('clears the deselected case drill-down in the same store update', () => {
    setEnemyCases(new Set<BattleCase>(['story', 'mirrorDungeon']))
    enemyFilterStore.setters.selectedChapters(new Set(['canto:8']))
    enemyFilterStore.setters.selectedThemePacks(new Set(['1023']))

    const updates: number[] = []
    const unsubscribe = enemyFilterStore.store.subscribe((state) => {
      updates.push(state.values.selectedChapters.size)
    })
    setEnemyCases(new Set<BattleCase>(['mirrorDungeon']))
    unsubscribe()

    const { values } = enemyFilterStore.store.getState()
    expect(updates).toEqual([0])
    expect([...values.selectedCases]).toEqual(['mirrorDungeon'])
    expect(values.selectedChapters.size).toBe(0)
    expect([...values.selectedThemePacks]).toEqual(['1023'])
  })
})
