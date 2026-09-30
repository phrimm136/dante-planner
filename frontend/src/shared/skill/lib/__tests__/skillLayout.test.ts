import { describe, expect, it } from 'vitest'

import { skillLowerLayout, skillUpperLayout } from '../skillLayout'

const upper = skillUpperLayout()

describe('skillUpperLayout', () => {
  it('pads the card by the 15-unit group overhang above a 270-unit upper block', () => {
    expect(upper.card).toEqual({ paddingTop: '0.3281rem' })
    expect(upper.block).toEqual({ height: '5.9063rem' })
  })

  it('places the skill icon frame at (65.4, 23.26), 252 units square', () => {
    expect(upper.icon).toEqual({
      left: '1.4306rem',
      top: '0.5088rem',
      width: '5.5125rem',
      height: '5.5125rem',
    })
  })

  it('centres the attack icon on (184.4, 218.7), 62.4 units square', () => {
    expect(upper.attackIcon).toEqual({
      left: '4.0337rem',
      top: '4.7841rem',
      width: '1.365rem',
      height: '1.365rem',
      transform: 'translate(-50%, -50%)',
    })
  })

  it('centres the coin-scale value and its backing', () => {
    expect(upper.coinScale).toEqual({
      left: '3.9769rem',
      top: '1.1134rem',
      transform: 'translate(-50%, -50%)',
      fontSize: '1.3125rem',
      letterSpacing: '-0.06em',
    })
    expect(upper.coinScaleBg).toEqual({
      left: '4.0622rem',
      top: '1.1812rem',
      width: '2.1368rem',
      height: '1.3228rem',
      transform: 'translate(-50%, -50%)',
    })
  })

  it('right-aligns the base power to x 104 and centres its digit ink on y 142.7', () => {
    expect(upper.basePower).toEqual({
      left: '0',
      width: '2.275rem',
      top: '3.1216rem',
      transform: 'translateY(calc(-50% + -0.1531em))',
      textAlign: 'right',
      fontSize: '2.2431rem',
    })
  })

  it('starts the coin row at (271.19, 14.3)', () => {
    expect(upper.coins).toEqual({ left: '5.9323rem', top: '0.3128rem' })
  })

  it('centres the name plate on y 111.3 and keeps its area 100 units clear of the right edge', () => {
    expect(upper.nameArea).toEqual({ left: '5.985rem', top: '1.6764rem', right: '2.1875rem' })
  })

  it('places the level icon and number', () => {
    expect(upper.levelIcon).toEqual({
      left: '6.0747rem',
      top: '3.2813rem',
      width: '1.666rem',
      height: '1.8878rem',
    })
    expect(upper.levelText).toEqual({
      left: '7.9778rem',
      top: '4.2rem',
      transform: 'translateY(-50%)',
      fontSize: '1.6406rem',
      maxWidth: '1.8327rem',
      textDecorationThickness: '0.1094rem',
    })
  })

  it('places the attack-weight label and count', () => {
    expect(upper.targetLabel).toEqual({
      left: '9.9663rem',
      top: '3.8084rem',
      width: '3.9602rem',
      height: '0.8313rem',
      fontSize: '0.7219rem',
    })
    expect(upper.targetCount).toEqual({
      left: '14.0844rem',
      top: '3.8084rem',
      width: '8.2556rem',
      height: '0.8313rem',
      fontSize: '0.7722rem',
      columnGap: '0.1794rem',
    })
    expect(upper.targetSquare).toEqual({ width: '0.5381rem', height: '0.5381rem' })
  })

  it('puts the sanity cost under the attack-weight label', () => {
    expect(upper.sanityCost).toEqual({ left: '9.9663rem', top: '4.6397rem', fontSize: '0.7219rem' })
  })
})

describe('skillLowerLayout', () => {
  it('insets the lower block 30 units on each side', () => {
    expect(skillLowerLayout(false).block).toEqual({ paddingInline: '0.6563rem' })
  })

  it.each([
    [false, '0.3281rem'],
    [true, '0.9844rem'],
  ])('pads the card bottom (importance %s) by %s', (hasImportance, paddingBottom) => {
    expect(skillLowerLayout(hasImportance).card).toEqual({ paddingBottom })
  })

  it('places the lock at R 49 / B 83, 250 × 355, alpha .275', () => {
    expect(skillLowerLayout(false).lock).toEqual({
      right: '1.0719rem',
      bottom: '1.8156rem',
      width: '5.4688rem',
      height: '7.7656rem',
      opacity: 0.275,
    })
  })
})
