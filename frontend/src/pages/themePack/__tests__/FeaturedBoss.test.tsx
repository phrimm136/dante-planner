import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'

import type { ThemePackDetail } from '@/pages/themePack'
import { FeaturedBoss } from '../ThemePackDetailPage'

// The hash-static-assets Vite plugin rewrites resolveAsset(...) call sites at
// transform time, so getFeaturedBossImagePath returns "/missing-asset" at runtime
// for any asset absent from the build manifest. Mock the helper to assert the
// component passes packId + portraitId through and builds one img per entry.
vi.mock('@/shared/assets', () => ({
  getFeaturedBossImagePath: (packId: string, portraitId: number | string) =>
    `/images/featuredBoss/${packId}_${portraitId}.webp`,
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => fallback ?? key,
      i18n: { language: 'EN' },
    }),
  }
})

describe('FeaturedBoss', () => {
  const roster: ThemePackDetail['featuredBosses'] = [
    { unitId: 71001, portraitId: 71001 },
    { unitId: 1058, portraitId: '8605' },
    { unitId: 8002, portraitId: 8002 },
  ]

  it('renders one img per manifest entry with the expected src (numeric and string portraitId)', () => {
    const { container } = render(<FeaturedBoss packId="1001" bosses={roster} />)

    const imgs = Array.from(container.querySelectorAll('img'))
    expect(imgs).toHaveLength(roster.length)
    for (const boss of roster) {
      const img = imgs.find((el) =>
        el.getAttribute('src')?.includes(`featuredBoss/1001_${boss.portraitId}.webp`),
      )
      expect(img).toBeDefined()
      expect(img).toHaveAttribute('loading', 'lazy')
    }
  })

  it('iterates featuredBosses only — img count equals roster length (Invariant 2)', () => {
    const { container } = render(<FeaturedBoss packId="1001" bosses={roster} />)
    expect(container.querySelectorAll('img')).toHaveLength(roster.length)
  })

  it('renders nothing for an empty roster (no SectionTitle, no img)', () => {
    const { container } = render(<FeaturedBoss packId="1001" bosses={[]} />)
    expect(container).toBeEmptyDOMElement()
    expect(container.querySelectorAll('img')).toHaveLength(0)
  })
})
