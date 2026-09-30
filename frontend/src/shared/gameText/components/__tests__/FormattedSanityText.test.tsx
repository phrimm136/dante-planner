import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { FormattedSanityText } from '../FormattedSanityText'

function spanOf(text: string): HTMLElement {
  return render(<FormattedSanityText text={text} className="sanity" />).container
    .firstChild as HTMLElement
}

function styled(root: HTMLElement): { text: string; color: string; fontSize: string }[] {
  return [...root.querySelectorAll<HTMLElement>('span > span')].map((span) => ({
    text: span.textContent ?? '',
    color: span.style.getPropertyValue('--rt-color'),
    fontSize: span.style.fontSize,
  }))
}

describe('FormattedSanityText', () => {
  it('reads a size tag inside a colour as a smaller run of that colour', () => {
    const root = spanOf(
      '<color=red>Increases when hit by an enemy\n<size=95%>(Base Value is {0}, raised by up to {1} per Turn)</size></color>',
    )

    expect(styled(root)).toEqual([
      { text: 'Increases when hit by an enemy', color: '#ff0000', fontSize: '' },
      {
        text: '(Base Value is {0}, raised by up to {1} per Turn)',
        color: '#ff0000',
        fontSize: '95%',
      },
    ])
    expect(root.querySelectorAll('br')).toHaveLength(1)
  })

  it('keeps the size of a colour run inside a size tag', () => {
    const root = spanOf(
      'Increases after winning a Clash based on the Clash count\n<size=95%>(Base Value is {0}, raised by <color=red>{1}%</color> per Clash count after 1)</size>',
    )

    expect(styled(root)).toEqual([
      { text: '(Base Value is {0}, raised by ', color: '', fontSize: '95%' },
      { text: '{1}%', color: '#ff0000', fontSize: '95%' },
      { text: ' per Clash count after 1)', color: '', fontSize: '95%' },
    ])
    expect(root).toHaveClass('sanity')
  })

  it('renders nothing for empty text', () => {
    expect(render(<FormattedSanityText text="" />).container.innerHTML).toBe('')
  })
})
