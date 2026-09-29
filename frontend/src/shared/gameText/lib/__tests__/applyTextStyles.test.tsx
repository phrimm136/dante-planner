import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { applyTextStyles } from '../unityRichText'

function renderStyled(text: string): HTMLElement {
  return render(<p>{applyTextStyles(text)}</p>).container
}

describe('applyTextStyles', () => {
  it('draws an OrangeMark run in the client style and keeps strikethrough inside it', () => {
    const container = renderStyled('Gain <style="OrangeMark">2 <s>old</s> Haste</style> now')

    const mark = container.querySelector<HTMLElement>('[data-text-style="OrangeMark"]')
    expect(mark?.textContent).toBe('2 old Haste')
    expect(mark?.querySelector('s')?.textContent).toBe('old')
    expect(mark?.style.textDecoration).toBe('underline')
    expect(container.textContent).toBe('Gain 2 old Haste now')
  })

  it('renders an unknown style as its text and leaves unstyled text alone', () => {
    expect(renderStyled('a <style="Fancy">b</style> c').textContent).toBe('a b c')
    expect(applyTextStyles('plain')).toBe('plain')
  })
})
