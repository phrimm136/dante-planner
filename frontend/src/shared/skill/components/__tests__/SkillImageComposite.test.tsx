import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'

import { SkillImageComposite } from '../SkillImageComposite'

function renderComposite(skillImagePath: string | undefined, showMissingPlaceholder = false) {
  return render(
    <SkillImageComposite
      skillImagePath={skillImagePath}
      attributeType="SCARLET"
      skillTier={2}
      atkType="PENETRATE"
      basePower={3}
      coinPower={2}
      showMissingPlaceholder={showMissingPlaceholder}
    />,
  )
}

describe('SkillImageComposite', () => {
  it('draws the skill image inside the frame when it has a path', () => {
    const { container } = renderComposite('/images/identity/10310/1031001.webp')
    expect(container.querySelector('img[src="/images/identity/10310/1031001.webp"]')).not.toBeNull()
  })

  it('draws the frame only, with no image and no placeholder, when it has no path', () => {
    const { container } = renderComposite(undefined, true)
    expect(container.querySelector('img.object-cover')).toBeNull()
    expect(screen.queryByText('Missing')).toBeNull()
    expect(container.querySelectorAll('img').length).toBeGreaterThan(0)
  })
})
