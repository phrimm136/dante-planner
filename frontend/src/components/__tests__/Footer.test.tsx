import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import { Footer } from '../Footer'

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('Footer', () => {
  it('links to the font licences page with its i18n label', async () => {
    mountRoute('/', '/', Footer)

    const link = await screen.findByRole('link', { name: 'Font Licenses' })

    expect(link).toHaveAttribute('href', '/licenses')
  })
})
