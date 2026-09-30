import { describe, expect, it } from 'vitest'

import { conditionLayout, panicLayout } from '../sanityLayout'

describe('panic column', () => {
  it('places the tag, icon and name at fixed client offsets', () => {
    const layout = panicLayout()
    expect(layout.tagBg).toEqual({
      position: 'absolute',
      left: '0.2734rem',
      top: '0.7875rem',
      width: '6.0156rem',
      height: '1.5925rem',
    })
    expect(layout.tagText).toEqual({
      position: 'absolute',
      left: '0.6563rem',
      top: '0.9844rem',
      width: '5.25rem',
      height: '1.3125rem',
      fontSize: '0.9844rem',
      whiteSpace: 'nowrap',
    })
    expect(layout.icon).toEqual({
      position: 'absolute',
      left: '1.0544rem',
      top: '2.9531rem',
      width: '4.235rem',
      height: '3.9397rem',
    })
    expect(layout.name).toEqual({
      position: 'absolute',
      left: '0.2078rem',
      top: '7.1225rem',
      width: '6.0156rem',
      height: '2.3029rem',
      padding: '0.1094rem 0.4375rem 0rem',
    })
  })

  it('offsets the description and floors the section height', () => {
    const layout = panicLayout()
    expect(layout.desc).toEqual({
      lineHeight: 1.394,
      letterSpacing: '-0.03em',
      wordSpacing: '-0.05em',
      color: '#ebcaa2',
      position: 'relative',
      top: '0.3719rem',
      marginLeft: '6.5625rem',
      paddingTop: '2.5187rem',
      marginBottom: '-0.1063rem',
    })
    expect(layout.section).toEqual({
      position: 'relative',
      minHeight: '9.8438rem',
      marginBottom: '1.0938rem',
    })
  })
})

describe('condition sections', () => {
  it.each([
    ['add', '1.6406rem', '#80c9ff'],
    ['min', '1.6187rem', '#f94b48'],
  ] as const)('clamps the %s tag width and fixes its height at %s', (kind, height, color) => {
    expect(conditionLayout(kind).tag).toEqual({
      position: 'absolute',
      left: '0.2734rem',
      top: '0rem',
      height,
      minWidth: '8.8594rem',
      maxWidth: '12.5781rem',
      paddingInline: '0.5469rem',
      color,
    })
  })

  it.each([
    ['add', '0rem', '0.2293rem', '-0.05em'],
    ['min', '0.6779rem', '0.306rem', '-0.1em'],
  ] as const)('insets the %s list right %s and left %s', (kind, right, left, wordSpacing) => {
    expect(conditionLayout(kind).list).toEqual({
      lineHeight: 1.394,
      letterSpacing: '-0.02em',
      wordSpacing,
      display: 'flex',
      flexDirection: 'column',
      rowGap: '0.7313rem',
      paddingTop: '2.0812rem',
      marginBottom: '-0.1063rem',
      paddingRight: right,
      paddingLeft: left,
    })
  })

  it.each([
    ['add', '1.0938rem', '6.2772rem'],
    ['min', '1.0933rem', '6.2781rem'],
  ] as const)('anchors the %s icon bottom-right at %s, height %s', (kind, right, height) => {
    expect(conditionLayout(kind).icon).toEqual({
      position: 'absolute',
      right,
      bottom: '0rem',
      width: '6.125rem',
      height,
      opacity: 0.275,
    })
  })

  it.each(['add', 'min'] as const)('floors the %s section and follows it by the gap', (kind) => {
    expect(conditionLayout(kind).section).toEqual({
      position: 'relative',
      minHeight: '6.5625rem',
      marginBottom: '1.0938rem',
    })
  })
})
