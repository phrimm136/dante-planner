import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

import i18n from '@/lib/i18n'
import { entityListI18nOptions } from '@/shared/entityCatalog'
import { useSearchTermSources } from '@/shared/filter'
import { THEME_PACK_LIST_I18N, useThemePackListI18n } from '@/shared/themePack'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { THEME_PACK_LIST } from '../useThemePackListData'

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('theme pack list i18n', () => {
  it('keys the slice config and the shared config to one query', () => {
    expect(entityListI18nOptions(THEME_PACK_LIST, 'EN').queryKey).toEqual(
      entityListI18nOptions(THEME_PACK_LIST_I18N, 'EN').queryKey,
    )
    expect(entityListI18nOptions(THEME_PACK_LIST_I18N, 'EN').queryKey).toEqual([
      'themePack',
      'list',
      'i18n',
      'EN',
    ])
  })

  it('shares one cache entry between the shared hook and the slice search terms', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(
      () => ({
        slice: useSearchTermSources(THEME_PACK_LIST, {}, false).names,
        shared: useThemePackListI18n(),
      }),
      { wrapper },
    )

    await waitFor(() => {
      expect(result.current.shared['1001']?.name).toBeDefined()
    })
    expect(result.current.slice).toBe(result.current.shared)
    const i18nQueries = queryClient
      .getQueryCache()
      .findAll({ queryKey: ['themePack', 'list', 'i18n'] })
    expect(i18nQueries.map((query) => query.queryKey)).toEqual([
      ['themePack', 'list', 'i18n', 'EN'],
    ])
  })
})
