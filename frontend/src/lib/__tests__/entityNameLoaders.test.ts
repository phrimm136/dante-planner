import { describe, it, expect, beforeAll } from 'vitest'

import i18n from '@/lib/i18n'
import {
  loadAbEventTitle,
  loadEgoGiftName,
  loadEgoName,
  loadIdentityName,
} from '@/lib/entityNameLoaders'

beforeAll(async () => {
  await i18n.changeLanguage('EN')
})

describe('entity name loaders', () => {
  it('joins the lines of an identity name', async () => {
    expect(await loadIdentityName({ params: { id: '10101' } })).toEqual({ name: 'LCB Sinner' })
  })

  it('loads an EGO name', async () => {
    expect(await loadEgoName({ params: { id: '20101' } })).toEqual({ name: 'Crow’s Eye View' })
  })

  it('loads an EGO gift name', async () => {
    expect(await loadEgoGiftName({ params: { id: '9001' } })).toEqual({
      name: 'Hellterfly’s Dream',
    })
  })

  it('cuts an abnormality event description to its first 20 characters', async () => {
    expect(await loadAbEventTitle({ params: { id: '901001' } })).toEqual({
      title: 'Orange circles float...',
    })
  })

  it('falls back to the id when the abnormality event file is missing', async () => {
    expect(await loadAbEventTitle({ params: { id: '999999' } })).toEqual({ title: '999999' })
  })
})
