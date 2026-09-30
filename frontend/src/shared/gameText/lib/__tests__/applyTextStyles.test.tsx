import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { applyTextStyles } from '../unityRichText'

function renderStyled(text: string): HTMLElement {
  return render(<p>{applyTextStyles(text)}</p>).container
}

describe('applyTextStyles', () => {
  it('draws a mark run with its tag state and strikes the struck run inside it', () => {
    const container = renderStyled(
      'Gain <color=#ff6000><mark=#ff000040><b><u>2 <s>old</s> Haste</u></b></mark></color> now',
    )

    const marks = [...container.querySelectorAll<HTMLElement>('.rt-mark')]
    expect(marks.map((mark) => mark.textContent)).toEqual(['2 ', 'old', ' Haste'])
    expect(marks[1]?.style.textDecorationLine).toBe('underline line-through')
    expect(marks[0]?.style.textDecorationLine).toBe('underline')
    expect(container.textContent).toBe('Gain 2 old Haste now')
  })

  it('renders a named style as its text and leaves unstyled text alone', () => {
    expect(renderStyled('a <style="OrangeMark">b</style> c').textContent).toBe('a b c')
    expect(renderStyled('a <style="OrangeMark">b</style> c').querySelector('.rt-mark')).toBeNull()
    expect(applyTextStyles('plain')).toBe('plain')
  })

  it('breaks lines at newlines', () => {
    const container = renderStyled('a\nb')

    expect(container.querySelectorAll('br')).toHaveLength(1)
    expect(container.textContent).toBe('ab')
  })
})
