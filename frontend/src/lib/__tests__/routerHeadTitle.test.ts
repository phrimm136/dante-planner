/**
 * The language-change listener reads each match's published `meta` instead of
 * re-deriving head() from the router's private route table. Two things have to
 * hold for that to be sound, and both are pinned here: matches carry the meta
 * their head() produced, and `router.invalidate()` republishes it from freshly
 * re-run loaders.
 */

import { describe, it, expect } from 'vitest'
import {
  createRouter,
  createRootRoute,
  createRoute,
  createMemoryHistory,
} from '@tanstack/react-router'

import { titleFromMatches } from '../routerTitle'

function buildRouter(readLang: () => string) {
  const rootRoute = createRootRoute({
    head: () => ({ meta: [{ title: 'root' }, { name: 'description', content: 'x' }] }),
  })
  const detailRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/thing/$id',
    loader: ({ params }) => ({ name: `${readLang()}-${params.id}` }),
    head: ({ loaderData }) => ({ meta: [{ title: `${loaderData?.name} | suffix` }] }),
  })
  const untitledRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/plain',
  })

  return createRouter({
    routeTree: rootRoute.addChildren([detailRoute, untitledRoute]),
    history: createMemoryHistory({ initialEntries: ['/thing/42'] }),
  })
}

describe('titleFromMatches', () => {
  it('returns the deepest published title', () => {
    expect(titleFromMatches([{ meta: [{ title: 'root' }] }, { meta: [{ title: 'leaf' }] }])).toBe(
      'leaf',
    )
  })

  it('skips matches that published no meta and falls back to a shallower one', () => {
    expect(
      titleFromMatches([{ meta: [{ title: 'root' }] }, { meta: undefined }, { meta: [] }]),
    ).toBe('root')
  })

  it('stops at the deepest meta-carrying match even when it has no title', () => {
    expect(
      titleFromMatches([
        { meta: [{ title: 'root' }] },
        { meta: [{ name: 'description', content: 'x' }] },
      ]),
    ).toBeNull()
  })

  it('returns null for no matches', () => {
    expect(titleFromMatches([])).toBeNull()
  })
})

describe('router match meta', () => {
  it('publishes head() output onto the match, including loader data', async () => {
    const router = buildRouter(() => 'EN')
    await router.load()

    expect(titleFromMatches(router.state.matches)).toBe('EN-42 | suffix')
  })

  it('republishes meta from re-run loaders after invalidate', async () => {
    let lang = 'EN'
    const router = buildRouter(() => lang)
    await router.load()
    expect(titleFromMatches(router.state.matches)).toBe('EN-42 | suffix')

    lang = 'KR'
    await router.invalidate()

    expect(titleFromMatches(router.state.matches)).toBe('KR-42 | suffix')
  })
})
