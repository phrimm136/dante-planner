import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import enemy1339 from '@static/data/enemy/1339.json'
import { getCoinSpritePath } from '@/shared/assets'
import { SkillEntrySchema } from '@/shared/gameData'
import type { SkillCoin } from '@/shared/gameData'
import { CoinDisplay } from '../CoinDisplay'

const coin = (color: string): SkillCoin => ({
  scale: 1,
  operator: 'ADD',
  grade: 1,
  color,
  special: false,
})

const sources = (container: HTMLElement): (string | null)[] =>
  Array.from(container.querySelectorAll('img')).map((img) => img.getAttribute('src'))

describe('CoinDisplay', () => {
  it.each(['GOLD', 'GREY', 'GREEN', 'PURPLE'] as const)(
    'draws a %s coin with its sprite',
    (color) => {
      const { container } = render(<CoinDisplay coins={[coin(color)]} />)
      expect(sources(container)).toEqual([getCoinSpritePath(color)])
    },
  )

  it('draws one sprite per coin in order', () => {
    const { container } = render(
      <CoinDisplay coins={[coin('GOLD'), coin('GREY'), coin('PURPLE'), coin('GREEN')]} />,
    )
    expect(sources(container)).toEqual([
      getCoinSpritePath('GOLD'),
      getCoinSpritePath('GREY'),
      getCoinSpritePath('PURPLE'),
      getCoinSpritePath('GREEN'),
    ])
  })

  it('renders nothing for a skill without coins', () => {
    const { container } = render(<CoinDisplay coins={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('draws enemy 1339 skill 133907 as two green coins', () => {
    const [phase] = enemy1339.units['1339'].phases
    const entry = SkillEntrySchema.parse(phase?.skills['133907'])
    const { container } = render(<CoinDisplay coins={entry.levels[0].coins} />)
    const green = getCoinSpritePath('GREEN')
    expect(sources(container)).toEqual([green, green])
  })

  it('sizes coins at 65 client units under the description-font scale and leaves the height to the sprite aspect', () => {
    const { container } = render(<CoinDisplay coins={[coin('GOLD'), coin('GREEN')]} />)
    const row = container.firstElementChild as HTMLElement
    expect(row.style.getPropertyValue('--coin-width')).toBe('1.4219rem')
    for (const img of container.querySelectorAll('img')) {
      expect([...img.classList]).toEqual(['w-(--coin-width)', 'h-auto'])
    }
  })

  it('overlaps every coin after the first by 10 client units', () => {
    const { container } = render(
      <CoinDisplay coins={[coin('GOLD'), coin('GREY'), coin('GREEN')]} />,
    )
    const row = container.firstElementChild as HTMLElement
    expect(row.style.getPropertyValue('--coin-gap')).toBe('-0.2188rem')
    const margins = Array.from(container.querySelectorAll('img')).map((img) =>
      img.style.getPropertyValue('margin-inline-start'),
    )
    expect(margins).toEqual(['', 'var(--coin-gap)', 'var(--coin-gap)'])
  })
})
