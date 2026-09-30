import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { ColoredText } from '../ColoredText'
import readings from './__fixtures__/coloredTextReadings.json'

type Run = [text: string, color: string | null, size: string | null]

function runsOf(root: HTMLElement): Run[] {
  const chars: Run[] = []
  const visit = (node: Node, color: string | null, size: string | null) => {
    if (node.nodeType === Node.TEXT_NODE) {
      for (const char of node.textContent ?? '') {
        chars.push(char === '\n' ? ['\n', null, null] : [char, color, size])
      }
      return
    }
    const element = node as HTMLElement
    if (element.tagName === 'BR') {
      chars.push(['\n', null, null])
      return
    }
    const ownColor = element.style.getPropertyValue('--rt-color') || null
    const ownSize = element.style.fontSize || null
    for (const child of element.childNodes) visit(child, ownColor ?? color, ownSize ?? size)
  }
  visit(root, null, null)

  const runs: Run[] = []
  for (const char of chars) {
    const last = runs[runs.length - 1]
    if (last && last[1] === char[1] && last[2] === char[2]) last[0] += char[0]
    else runs.push([...char])
  }
  return runs
}

function renderRuns(text: string): Run[] {
  const { container } = render(
    <div>
      <ColoredText text={text} />
    </div>,
  )
  return runsOf(container.firstChild as HTMLElement)
}

describe('ColoredText', () => {
  it.each(readings.map((reading) => [reading.input, reading.runs as Run[]] as const))(
    'renders shipped text %j as the grammar reader did',
    (input, runs) => {
      expect(renderRuns(input)).toEqual(runs)
    },
  )

  it('keeps prose angle brackets as text', () => {
    expect(renderRuns('<This is...> he said')).toEqual([['<This is...> he said', null, null]])
  })

  it('keeps an unclosed colour open to the end of the text', () => {
    expect(renderRuns('<color=#ff0000>a<color=#00ff00>b</color>c')).toEqual([
      ['a', '#ff0000', null],
      ['b', '#00ff00', null],
      ['c', '#ff0000', null],
    ])
  })

  it('renders a named colour as its TextMeshPro value', () => {
    expect(renderRuns('<color=red>x</color>')).toEqual([['x', '#ff0000', null]])
  })

  it('breaks lines with <br>', () => {
    const { container } = render(<ColoredText text={'a\nb'} />)

    expect(container.querySelectorAll('br')).toHaveLength(1)
    expect(container.textContent).toBe('ab')
  })
})
