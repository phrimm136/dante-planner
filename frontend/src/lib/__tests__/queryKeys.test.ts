import { describe, it, expect } from 'vitest'

import { createEntityListQueryKeys, createEntityDetailQueryKeys } from '../queryKeys'

describe('createEntityListQueryKeys', () => {
  const keys = createEntityListQueryKeys('identity')

  it('all() is [ns, "list"]', () => {
    expect(keys.all()).toEqual(['identity', 'list'])
  })

  it('spec() is [ns, "list", "spec"]', () => {
    expect(keys.spec()).toEqual(['identity', 'list', 'spec'])
  })

  it('i18n(language) is [ns, "list", "i18n", language]', () => {
    expect(keys.i18n('KR')).toEqual(['identity', 'list', 'i18n', 'KR'])
  })
})

describe('createEntityDetailQueryKeys', () => {
  const keys = createEntityDetailQueryKeys('identity')

  it('all() is [ns]', () => {
    expect(keys.all()).toEqual(['identity'])
  })

  it('detail(id) is [ns, id]', () => {
    expect(keys.detail('10101')).toEqual(['identity', '10101'])
  })

  it('i18n(id, language) is [ns, id, "i18n", language]', () => {
    expect(keys.i18n('10101', 'EN')).toEqual(['identity', '10101', 'i18n', 'EN'])
  })
})

describe('namespace isolation', () => {
  it('different namespaces never produce overlapping keys', () => {
    const identity = createEntityListQueryKeys('identity')
    const ego = createEntityListQueryKeys('ego')

    expect(identity.all()).not.toEqual(ego.all())
    expect(identity.spec()).not.toEqual(ego.spec())
  })
})
