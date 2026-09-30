import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { StyledNameSkeleton, StyledSkillName, type PlateGeometry } from '@/shared/gameText'

const PLATE: PlateGeometry = {
  height: '1.5166rem',
  fontSize: '0.9844rem',
  paddingLeft: '0.4375rem',
  paddingRight: '1.0938rem',
  paddingBottom: '0.2188rem',
}

function nameParts(container: HTMLElement): { body: HTMLElement; name: HTMLElement } {
  const plate = container.querySelector<HTMLElement>('[data-slot="name-plate"]')
  const body = plate?.firstElementChild as HTMLElement
  return { body, name: body.querySelector('span') as HTMLElement }
}

function skeletonBody(container: HTMLElement): HTMLElement {
  return container.firstElementChild?.firstElementChild?.firstElementChild as HTMLElement
}

describe('StyledSkillName plate geometry', () => {
  it('keeps the page plate when no client plate is given', () => {
    const { container } = render(<StyledSkillName name="Name" attributeType="NEUTRAL" />)
    const { body, name } = nameParts(container)
    expect(body.style.padding).toBe('0.1em 3.75em 0px 0.3em')
    expect(body.style.height).toBe('')
    expect(name.style.fontSize).toBe('20px')
  })

  it('draws the client plate with the given lengths', () => {
    const { container } = render(
      <StyledSkillName name="Name" attributeType="NEUTRAL" plate={PLATE} />,
    )
    const { body, name } = nameParts(container)
    expect(body.style.height).toBe(PLATE.height)
    expect(body.style.paddingLeft).toBe(PLATE.paddingLeft)
    expect(body.style.paddingRight).toBe(PLATE.paddingRight)
    expect(body.style.paddingBottom).toBe(PLATE.paddingBottom)
    expect(body.style.paddingTop).toBe('0.1em')
    expect(name.style.fontSize).toBe(PLATE.fontSize)
  })
})

describe('StyledNameSkeleton plate geometry', () => {
  it('keeps the page plate when no client plate is given', () => {
    const { container } = render(<StyledNameSkeleton attributeType="NEUTRAL" />)
    const body = skeletonBody(container)
    expect(body.style.padding).toBe('0.3em 3.75em 0.3em 10px')
    expect(body.style.height).toBe('')
    expect(body.style.fontSize).toBe('')
  })

  it('draws the client plate with the given lengths', () => {
    const { container } = render(<StyledNameSkeleton attributeType="NEUTRAL" plate={PLATE} />)
    const body = skeletonBody(container)
    expect(body.style.height).toBe(PLATE.height)
    expect(body.style.fontSize).toBe(PLATE.fontSize)
    expect(body.style.paddingLeft).toBe(PLATE.paddingLeft)
    expect(body.style.paddingRight).toBe(PLATE.paddingRight)
    expect(body.style.paddingBottom).toBe(PLATE.paddingBottom)
    expect(body.style.paddingTop).toBe('0.3em')
  })
})
