import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { isCoinColor } from '../coinColor'

const DATA_DIR = resolve(import.meta.dirname, '../../../../../../static/data')

function collectCoinColors(node: unknown, found: Set<string>): void {
  if (Array.isArray(node)) {
    for (const item of node) collectCoinColors(item, found)
    return
  }
  if (node === null || typeof node !== 'object') return
  for (const [key, value] of Object.entries(node)) {
    if (key === 'coins' && Array.isArray(value)) {
      for (const coin of value as { color: string }[]) found.add(coin.color)
    }
    collectCoinColors(value, found)
  }
}

describe('isCoinColor', () => {
  it('accepts the four client coin colors and rejects others', () => {
    expect(['GOLD', 'GREY', 'GREEN', 'PURPLE'].every(isCoinColor)).toBe(true)
    expect(isCoinColor('RED')).toBe(false)
  })

  it.each(['identity', 'ego', 'enemy', 'assistant'])(
    'covers every coin color shipped in %s data',
    (entity) => {
      const dir = join(DATA_DIR, entity)
      const found = new Set<string>()
      for (const file of readdirSync(dir).filter((name) => name.endsWith('.json'))) {
        collectCoinColors(JSON.parse(readFileSync(join(dir, file), 'utf8')), found)
      }
      expect(found.size).toBeGreaterThan(0)
      expect([...found].filter((color) => !isCoinColor(color))).toEqual([])
    },
  )
})
