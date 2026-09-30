import { describe, expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

import enemy1339 from '@static/data/enemy/1339.json'
import { SkillEntrySchema } from '@/shared/gameData'
import { toSkillCardData } from '../../lib/skillLevel'
import { SkillCard } from '../SkillCard'

const [phase] = enemy1339.units['1339'].phases
const entry = SkillEntrySchema.parse(phase?.skills['133907'])
const [level] = entry.levels
const IMAGE = '/images/battleSkill/absent.webp'

function renderCard(missingImage?: 'placeholder' | 'frame') {
  return render(
    <SkillCard
      skillId="133907"
      skillData={toSkillCardData(entry, level)}
      coins={level.coins}
      skillImagePath={IMAGE}
      skillTier={entry.skillTier}
      nameSlot={<span>Name</span>}
      descriptionSlot={<span>Desc</span>}
      unitLevel={60}
      importanceLevel={0}
      palette="enemy"
      {...(missingImage !== undefined && { missingImage })}
    />,
  )
}

function skillImage(container: HTMLElement): HTMLImageElement {
  const image = container.querySelector<HTMLImageElement>(`img[src="${IMAGE}"]`)
  if (image === null) throw new Error('no skill image')
  return image
}

describe('SkillCard with a skill image that fails to load', () => {
  it('shows the missing placeholder by default', () => {
    const { container } = renderCard()
    fireEvent.error(skillImage(container))

    expect(screen.getByText('Missing')).toBeInTheDocument()
  })

  it('keeps only the frame when asked to', () => {
    const { container } = renderCard('frame')
    fireEvent.error(skillImage(container))

    expect(container.querySelector(`img[src="${IMAGE}"]`)).toBeNull()
    expect(screen.queryByText('Missing')).toBeNull()
  })
})
