import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'

import krTable from '@static/data/fontAdvances/KR.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { NamePlate, NamePlateSkeleton, StyledSkillName } from '@/shared/gameText'

const KR_TABLE = FontAdvanceTableSchema.parse(krTable)

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => KR_TABLE,
}))

function nameParts(container: HTMLElement): {
  plate: HTMLElement
  body: HTMLElement
  name: HTMLElement
} {
  const plate = container.querySelector<HTMLElement>('[data-slot="name-plate"]') as HTMLElement
  const body = plate.firstElementChild as HTMLElement
  return { plate, body, name: body.querySelector('span') as HTMLElement }
}

describe('StyledSkillName', () => {
  it('keeps the page plate', () => {
    const { container } = render(<StyledSkillName name="Name" attributeType="NEUTRAL" />)
    const { plate, body, name } = nameParts(container)
    expect(plate.style.marginBottom).toBe('10px')
    expect(body.style.padding).toBe('0.1em 3.75em 0px 0.3em')
    expect(name.style.fontSize).toBe('20px')
  })
})

describe('NamePlate', () => {
  it('draws the passive name in the 72.74-unit plate with 30 and 100 unit margins at 40 units', () => {
    const { container } = render(<NamePlate kind="passive" name="Name" attributeType="NEUTRAL" />)
    const { body, name } = nameParts(container)
    expect(body.style.height).toBe('1.5912rem')
    expect(body.style.paddingLeft).toBe('0.6563rem')
    expect(body.style.paddingRight).toBe('2.1875rem')
    expect(name.style.fontSize).toBe('0.875rem')
  })

  it('draws the skill name in the same plate with 20 and 50 unit margins at 45 units', () => {
    const { container } = render(
      <NamePlate kind="skill" name="전원, 처형이다!!!" attributeType="CRIMSON" />,
    )
    const { body, name } = nameParts(container)
    expect(body.style.height).toBe('1.5912rem')
    expect(body.style.paddingLeft).toBe('0.4375rem')
    expect(body.style.paddingRight).toBe('1.0938rem')
    expect(name.style.fontSize).toBe('0.9844rem')
  })

  it('keeps the skill name in the 60-unit box above the 10-unit bottom margin', () => {
    const { container } = render(<NamePlate kind="skill" name="Name" attributeType="NEUTRAL" />)
    const { body } = nameParts(container)
    expect(body.style.paddingTop).toBe('0.03rem')
    expect(body.style.paddingBottom).toBe('0.2487rem')
    expect(body.style.alignItems).toBe('center')
  })

  it('starts the skill plate body 8 units right of its left edge', () => {
    const { container } = render(<NamePlate kind="skill" name="Name" attributeType="NEUTRAL" />)
    const { body } = nameParts(container)
    expect(body.style.backgroundSize).toBe('calc(100% - 0.175rem) 100%')
    expect(body.style.backgroundPosition).toBe('right top')
  })

  it('draws the name with the node spacing on one line and a hard offset underlay', () => {
    const { container } = render(
      <NamePlate kind="passive" name="전원, 처형이다!!!" attributeType="CRIMSON" />,
    )
    const { body, name } = nameParts(container)
    expect(name.style.letterSpacing).toBe('-0.02em')
    expect(name.style.wordSpacing).toBe('-0.05em')
    expect(name.style.whiteSpace).toBe('nowrap')
    expect(name.style.textShadow).toMatch(/em 0(px)? /)
    expect(body.style.textShadow).toBe('')
    expect(name.textContent).toBe('전원, 처형이다!!!')
  })

  it('cuts the tail 1.5 rem in and starts the solid body 3.4 rem in', () => {
    const { container } = render(<NamePlate kind="passive" name="Name" attributeType="NEUTRAL" />)
    const { plate, body } = nameParts(container)
    expect(plate.style.backgroundImage).toMatch(/255deg, transparent 1\.5rem/)
    expect(body.style.backgroundImage).toMatch(/transparent 3\.4rem, .+ 3\.4rem\)$/)
  })

  it('draws no banner without importance', () => {
    const { container } = render(
      <NamePlate kind="passive" name="Name" importance={{ level: 0, palette: 'player' }} />,
    )
    expect(container.querySelector('[data-slot="importance-banner"]')).toBeNull()
  })

  it('tints the plate of an important passive through the banner variables', () => {
    const { container } = render(
      <NamePlate kind="passive" name="Name" importance={{ level: 3, palette: 'player' }} />,
    )
    const banner = container.querySelector<HTMLElement>('[data-slot="importance-banner"]')
    expect(banner?.classList).toContain('importance-banner')
    expect(banner?.style.getPropertyValue('--name-body')).not.toBe('')
    expect(banner?.contains(nameParts(container).plate)).toBe(true)
  })
})

describe('NamePlateSkeleton', () => {
  it('sizes the loading plate as the name plate', () => {
    const { container } = render(<NamePlateSkeleton kind="passive" attributeType="NEUTRAL" />)
    const body = container.querySelector<HTMLElement>('[style*="height"]')
    expect(body?.style.height).toBe('1.5912rem')
    expect(body?.style.fontSize).toBe('0.875rem')
    expect(body?.style.paddingLeft).toBe('0.6563rem')
    expect(body?.style.paddingRight).toBe('2.1875rem')
  })
})
