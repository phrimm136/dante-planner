/**
 * EGOPassiveDisplay.test.tsx
 *
 * The EGOPassiveDisplay component renders the EGO passive list with optional
 * lore lines. These tests cover the flavor branch added this session and
 * the no-flavor fallback for the (currently dominant) unflavored EGOs.
 */

import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { EGOPassiveDisplay } from '../EGOPassiveDisplay'
import { FLAVOR_TEXT_COLOR } from '@/lib/constants'
import type { EGOPassiveI18n } from '../../types/EGOTypes'

describe('EGOPassiveDisplay', () => {
  const baseline: EGOPassiveI18n = {
    name: 'Fluid Sac',
    desc: 'On Awaken Lv 2+: gain X effect.',
  }

  it('renders nothing when passives are empty', () => {
    const { container } = render(<EGOPassiveDisplay passives={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders name and desc for each passive', () => {
    render(<EGOPassiveDisplay passives={[baseline]} />)
    expect(screen.getByText('Fluid Sac')).toBeInTheDocument()
    expect(screen.getByText('On Awaken Lv 2+: gain X effect.')).toBeInTheDocument()
  })

  it('omits the flavor element when the passive has no flavor', () => {
    render(<EGOPassiveDisplay passives={[baseline]} />)
    expect(screen.queryByTestId('passive-flavor')).not.toBeInTheDocument()
  })

  it('renders the flavor lore line when present', () => {
    render(
      <EGOPassiveDisplay
        passives={[{ ...baseline, flavor: 'A whispered prescript.' }]}
      />,
    )
    const flavor = screen.getByTestId('passive-flavor')
    expect(flavor).toBeInTheDocument()
    expect(flavor).toHaveTextContent('A whispered prescript.')
    expect(flavor).toHaveStyle({ color: FLAVOR_TEXT_COLOR })
  })

  it('renders flavor only for passives that carry it (mixed list)', () => {
    render(
      <EGOPassiveDisplay
        passives={[
          baseline,
          { ...baseline, name: 'Variant', flavor: 'Lore B.' },
        ]}
      />,
    )
    const flavors = screen.getAllByTestId('passive-flavor')
    expect(flavors).toHaveLength(1)
    expect(flavors[0]).toHaveTextContent('Lore B.')
  })

  it('omits the flavor element when flavor is an empty string', () => {
    render(<EGOPassiveDisplay passives={[{ ...baseline, flavor: '' }]} />)
    expect(screen.queryByTestId('passive-flavor')).not.toBeInTheDocument()
  })
})
