import { describe, expect, it } from 'vitest'

import { passiveBoxLayout } from '../passiveLayout'

describe('passiveBoxLayout', () => {
  const layout = passiveBoxLayout()

  it('floors the box at 300 units', () => {
    expect(layout.box).toEqual({ minHeight: '6.5625rem' })
  })

  it('keeps the plate 28 units from the left and 372 units clear of the right edge', () => {
    expect(layout.plateRow).toEqual({ marginLeft: '0.6125rem', marginRight: '8.1375rem' })
    expect(layout.plate).toEqual({
      height: '1.5912rem',
      fontSize: '0.875rem',
      paddingLeft: '0.6563rem',
      paddingRight: '2.1875rem',
      paddingBottom: '0rem',
    })
  })

  it('places the sin requirement row 20 units below the plate', () => {
    expect(layout.sinRow).toEqual({
      marginTop: '0.4375rem',
      marginLeft: '0.4375rem',
      height: '1.6844rem',
    })
    expect(layout.sinItem).toEqual({ minWidth: '3.7188rem' })
    expect(layout.sinIcon).toEqual({ width: '1.5313rem', height: '1.5313rem' })
    expect(layout.sinTimes).toEqual({ width: '0.875rem', height: '0.875rem' })
    expect(layout.sinCount.fontSize).toBe('1.5313rem')
    expect(layout.sinLabel).toEqual({ fontSize: '1.0938rem', marginLeft: '0.4375rem' })
  })

  it('insets the info text 27 units left and 20 right, 20 units below the header', () => {
    expect(layout.info).toEqual({
      marginTop: '0.4375rem',
      paddingLeft: '0.5906rem',
      paddingRight: '0.4375rem',
    })
  })

  it('sets the flavor at 33 units with 47 above and 35 below', () => {
    expect(layout.flavor).toEqual({
      fontSize: '0.7219rem',
      marginTop: '1.0281rem',
      marginBottom: '0.7656rem',
      paddingLeft: '0.5906rem',
      paddingRight: '0.4375rem',
    })
  })

  it('anchors the lock 20 units from the bottom-right corner', () => {
    expect(layout.lock).toEqual({
      right: '0.4375rem',
      bottom: '0.4375rem',
      width: '3.2091rem',
      height: '4.2678rem',
      opacity: 0.298,
    })
  })

  it('uses no length relative to the card width', () => {
    const lengths = Object.values(layout).flatMap((style: object) => Object.values(style))
    for (const value of lengths) {
      expect(String(value)).not.toMatch(/%|vw|cqw|calc/)
    }
  })
})
