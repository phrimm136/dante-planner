import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import fs from 'fs'
import os from 'os'
import path from 'path'
import type { OutputBundle } from 'rolldown'
import {
  appendRules,
  apiOriginOf,
  countRules,
  formatRule,
  literalDataImports,
  loaderImports,
  parseRouteTable,
  routeHeadersPlugin,
  routeLoadSet,
  toPagesPattern,
  type ChunkNode,
} from './vite-plugin-route-headers'

const ROUTER_SOURCE = `
import { createRootRoute, createRoute, lazyRouteComponent } from '@tanstack/react-router'
import { loadDetail } from '@/lib/routeLoaders'

const rootRoute = createRootRoute({ component: RootLayout })

const listRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/gesellschaft',
  component: lazyRouteComponent(() => import('@/pages/planner/ListPage')),
  pendingComponent: () => (
    <div className="page">
      <Skeleton />
    </div>
  ),
})

const detailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/gesellschaft/$id',
  component: lazyRouteComponent(() => import('@/pages/planner/DetailPage')),
  loader: loadDetail,
})

const routeTree = rootRoute.addChildren([listRoute, detailRoute])
`

const LOADERS_SOURCE = `
async function ensureList(config) {
  const [{ options }, cfg] = await Promise.all([import('@/shared/catalog/listData'), config])
  return queryClient.ensureQueryData(options(cfg))
}

function giftListSpec() {
  return ensureList(import('@/pages/gift/hooks/useGiftListData').then((m) => m.GIFT_LIST))
}

function unusedSpec() {
  return import('@/pages/unused/hooks/useUnused')
}

export async function loadDetail({ params }) {
  const name = await import(\`@static/i18n/\${i18n.language}/planner/\${params.id}.json\`)
  await Promise.allSettled([giftListSpec()])
  return { name }
}
`

const GIFT_HOOK_SOURCE = `
export const GIFT_LIST = {
  kind: 'gift',
  specImport: () => import('@static/data/giftSpecList.json'),
  i18nImport: (language: string) => import(\`@static/i18n/\${language}/giftNameList.json\`),
}
export const giftDetail = (id: string) => import(\`@static/data/gift/\${id}.json\`)
`

function chunk(fileName: string, fields: Partial<ChunkNode> = {}): ChunkNode {
  return { fileName, isEntry: false, facadeModuleId: null, moduleIds: [], imports: [], ...fields }
}

describe('parseRouteTable', () => {
  it('reads each route path, lazy page specifier and named loader', () => {
    expect(parseRouteTable(ROUTER_SOURCE)).toEqual([
      { path: '/planner/md/gesellschaft', page: '@/pages/planner/ListPage', loader: null },
      {
        path: '/planner/md/gesellschaft/$id',
        page: '@/pages/planner/DetailPage',
        loader: 'loadDetail',
      },
    ])
  })

  it('fails on a route whose component is not a literal lazy import', () => {
    const source = ROUTER_SOURCE.replace(
      "lazyRouteComponent(() => import('@/pages/planner/DetailPage'))",
      'lazyRouteComponent(() => import(pagePath))',
    )
    expect(() => parseRouteTable(source)).toThrow(
      /\/planner\/md\/gesellschaft\/\$id has no lazyRouteComponent/,
    )
  })

  it('fails on a route whose path is not a string literal', () => {
    const source = ROUTER_SOURCE.replace("path: '/planner/md/gesellschaft',", 'path: LIST_PATH,')
    expect(() => parseRouteTable(source)).toThrow(/no string-literal path/)
  })

  it('fails on a route nested under another route', () => {
    const source = ROUTER_SOURCE.replace(
      "getParentRoute: () => rootRoute,\n  path: '/planner/md/gesellschaft/$id'",
      "getParentRoute: () => listRoute,\n  path: '/planner/md/gesellschaft/$id'",
    )
    expect(() => parseRouteTable(source)).toThrow(/not a direct child of the root route/)
  })

  it('fails on a loader that is not a named function', () => {
    const source = ROUTER_SOURCE.replace('loader: loadDetail,', 'loader: () => loadDetail(),')
    expect(() => parseRouteTable(source)).toThrow(/loader that is not a named function/)
  })
})

describe('loaderImports', () => {
  it('follows calls into top-level helpers and keeps only string-literal imports', () => {
    expect(loaderImports(LOADERS_SOURCE, 'loadDetail').sort()).toEqual([
      '@/pages/gift/hooks/useGiftListData',
      '@/shared/catalog/listData',
    ])
  })

  it('fails on a loader name the loaders file does not define', () => {
    expect(() => loaderImports(LOADERS_SOURCE, 'loadMissing')).toThrow(
      /loadMissing is not a top-level function/,
    )
  })
})

describe('literalDataImports', () => {
  it('keeps literal language-neutral data imports and drops templated ones', () => {
    expect(literalDataImports(GIFT_HOOK_SOURCE, 'useGiftListData.ts')).toEqual([
      '@static/data/giftSpecList.json',
    ])
  })
})

describe('toPagesPattern', () => {
  it('turns TanStack $param segments into Pages :param placeholders', () => {
    expect(toPagesPattern('/planner/md/gesellschaft/$id')).toBe('/planner/md/gesellschaft/:id')
    expect(toPagesPattern('/planner/md/$id/edit')).toBe('/planner/md/:id/edit')
    expect(toPagesPattern('/identity')).toBe('/identity')
  })

  it('fails on a splat segment, which has no placeholder form', () => {
    expect(() => toPagesPattern('/files/$')).toThrow(/no Pages placeholder form/)
  })
})

describe('routeLoadSet', () => {
  const graph = [
    chunk('a/entry.js', {
      isEntry: true,
      facadeModuleId: '/app/index.html',
      imports: ['a/react.js'],
    }),
    chunk('a/react.js', { moduleIds: ['/node_modules/react/index.js'] }),
    chunk('a/page.js', {
      facadeModuleId: '/app/src/pages/DetailPage.tsx',
      moduleIds: ['/app/src/pages/DetailPage.tsx'],
      imports: ['a/react.js', 'a/shared.js'],
    }),
    chunk('a/shared.js', { moduleIds: ['/app/src/shared/viewer.tsx'], imports: ['a/react.js'] }),
    chunk('a/hook.js', { moduleIds: ['/app/src/pages/gift/hooks/useGiftListData.ts'] }),
    chunk('a/spec.js', {
      facadeModuleId: '/static/data/giftSpecList.json',
      moduleIds: ['/static/data/giftSpecList.json'],
    }),
    chunk('a/unrelated.js', { moduleIds: ['/app/src/pages/Other.tsx'], imports: ['a/react.js'] }),
  ]
  const route = {
    path: '/planner/md/gesellschaft/$id',
    pageModule: '/app/src/pages/DetailPage.tsx',
    loaderModules: ['/app/src/pages/gift/hooks/useGiftListData.ts'],
    dataModules: ['/static/data/giftSpecList.json'],
  }

  it('lists the page closure, loader modules and declared data, minus the entry closure', () => {
    expect(routeLoadSet(graph, route)).toEqual([
      'a/page.js',
      'a/shared.js',
      'a/hook.js',
      'a/spec.js',
    ])
  })

  it('fails when the page module is in no chunk', () => {
    expect(() => routeLoadSet(graph, { ...route, pageModule: '/app/src/pages/Gone.tsx' })).toThrow(
      /Gone.tsx is in no chunk/,
    )
  })

  it('fails when a hinted chunk holds a per-language file', () => {
    const withI18n = graph.map((c) =>
      c.fileName === 'a/shared.js'
        ? { ...c, moduleIds: [...c.moduleIds, '/static/i18n/EN/planner.json'] }
        : c,
    )
    expect(() => routeLoadSet(withI18n, route)).toThrow(
      /per-language \/static\/i18n\/EN\/planner.json/,
    )
  })
})

describe('formatRule', () => {
  it('puts the preconnect first and splits modulepreload hints across lines of at most 2000 characters', () => {
    const files = Array.from({ length: 120 }, (_, i) => `a/${String(i).padStart(12, '0')}.js`)
    const rule = formatRule('/planner/md/gesellschaft/:id', files, 'https://api.example.com')
    const [pattern, preconnect, ...links] = rule.split('\n')

    expect(pattern).toBe('/planner/md/gesellschaft/:id')
    expect(preconnect).toBe(
      '  Link: <https://api.example.com>; rel=preconnect; crossorigin=use-credentials',
    )
    expect(links.length).toBeGreaterThan(1)
    for (const line of links) {
      expect(line.startsWith('  Link: ')).toBe(true)
      expect(line.length).toBeLessThanOrEqual(2000)
    }
    const hinted = links.flatMap((line) =>
      [...line.matchAll(/<\/(a\/[^>]+)>; rel=modulepreload/g)].map((m) => m[1]),
    )
    expect(hinted).toEqual(files)
  })

  it('omits the preconnect without an API origin', () => {
    expect(formatRule('/terms', ['a/terms.js'], null)).toBe(
      '/terms\n  Link: </a/terms.js>; rel=modulepreload',
    )
  })
})

describe('apiOriginOf', () => {
  it('trims the base URL down to its origin', () => {
    expect(apiOriginOf(' https://api.example.com/ \n')).toBe('https://api.example.com')
    expect(apiOriginOf(undefined)).toBeNull()
    expect(apiOriginOf('  ')).toBeNull()
  })
})

describe('appendRules', () => {
  const existing =
    '# Security headers\n/*\n  X-Frame-Options: DENY\n\n/a/*\n  Cache-Control: immutable\n'

  it('counts only unindented, uncommented pattern lines as rules', () => {
    expect(countRules(existing)).toBe(2)
  })

  it('keeps the existing rules and appends the generated ones', () => {
    const merged = appendRules(existing, ['/terms\n  Link: </a/t.js>; rel=modulepreload'])
    expect(merged.startsWith(existing)).toBe(true)
    expect(merged).toContain('\n/terms\n  Link: </a/t.js>; rel=modulepreload\n')
    expect(countRules(merged)).toBe(3)
  })

  it('fails past the 100-rule limit', () => {
    const rules = Array.from(
      { length: 99 },
      (_, i) => `/r${i}\n  Link: </a/${i}.js>; rel=modulepreload`,
    )
    expect(() => appendRules(existing, rules)).toThrow(/101 rules; Pages allows 100/)
  })
})

describe('routeHeadersPlugin', () => {
  let root: string

  function write(relative: string, content: string) {
    const file = path.join(root, relative)
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(file, content)
  }

  function fakeResolve(specifier: string): { id: string } | null {
    const candidates = specifier.startsWith('@/')
      ? ['.ts', '.tsx'].map((ext) => path.join(root, 'src', specifier.slice(2) + ext))
      : specifier.startsWith('@static/')
        ? [path.join(root, 'static', specifier.slice('@static/'.length))]
        : []
    const id = candidates.find((candidate) => fs.existsSync(candidate))
    return id === undefined ? null : { id }
  }

  async function run(bundle: ChunkNode[]) {
    const plugin = routeHeadersPlugin({
      routerFile: path.join(root, 'src/lib/router.tsx'),
      loadersFile: path.join(root, 'src/lib/routeLoaders.ts'),
    })
    const hooks = plugin as unknown as {
      configResolved: (config: unknown) => void
      generateBundle: (this: unknown, output: unknown, bundle: OutputBundle) => Promise<void>
      closeBundle: () => Promise<void>
    }
    hooks.configResolved({
      root,
      build: { outDir: 'dist' },
      env: { VITE_API_BASE_URL: 'https://api.example.com/' },
    })
    const outputBundle = Object.fromEntries(
      bundle.map((c) => [c.fileName, { type: 'chunk', ...c }]),
    ) as unknown as OutputBundle
    await hooks.generateBundle.call(
      { resolve: async (s: string) => fakeResolve(s) },
      {},
      outputBundle,
    )
    await hooks.closeBundle()
    return fs.readFileSync(path.join(root, 'dist/_headers'), 'utf8')
  }

  beforeEach(() => {
    root = fs.mkdtempSync(path.join(os.tmpdir(), 'route-headers-test-'))
    write('src/lib/router.tsx', ROUTER_SOURCE)
    write('src/lib/routeLoaders.ts', LOADERS_SOURCE)
    write('src/shared/catalog/listData.ts', 'export const options = (cfg: unknown) => cfg\n')
    write('src/pages/gift/hooks/useGiftListData.ts', GIFT_HOOK_SOURCE)
    write(
      'src/pages/unused/hooks/useUnused.ts',
      "export const u = () => import('@static/data/unused.json')\n",
    )
    write('src/pages/planner/ListPage.tsx', 'export default 1\n')
    write('src/pages/planner/DetailPage.tsx', 'export default 2\n')
    write('static/data/giftSpecList.json', '{}')
    write('static/data/unused.json', '{}')
    write('dist/_headers', '/a/*\n  Cache-Control: public, max-age=31536000, immutable\n')
  })

  afterEach(() => {
    fs.rmSync(root, { recursive: true, force: true })
  })

  function graph(): ChunkNode[] {
    const src = (p: string) => path.join(root, 'src', p)
    return [
      chunk('a/entry.js', {
        isEntry: true,
        facadeModuleId: path.join(root, 'index.html'),
        moduleIds: [src('lib/router.tsx'), src('lib/routeLoaders.ts')],
        imports: ['a/vendor.js'],
      }),
      chunk('a/vendor.js', { moduleIds: ['/node_modules/react/index.js'] }),
      chunk('a/list.js', {
        facadeModuleId: src('pages/planner/ListPage.tsx'),
        moduleIds: [src('pages/planner/ListPage.tsx')],
        imports: ['a/vendor.js'],
      }),
      chunk('a/detail.js', {
        facadeModuleId: src('pages/planner/DetailPage.tsx'),
        moduleIds: [src('pages/planner/DetailPage.tsx')],
        imports: ['a/vendor.js', 'a/viewer.js'],
      }),
      chunk('a/viewer.js', { moduleIds: [src('pages/planner/Viewer.tsx')] }),
      chunk('a/catalog.js', { moduleIds: [src('shared/catalog/listData.ts')] }),
      chunk('a/gifthook.js', { moduleIds: [src('pages/gift/hooks/useGiftListData.ts')] }),
      chunk('a/giftspec.js', {
        facadeModuleId: path.join(root, 'static/data/giftSpecList.json'),
        moduleIds: [path.join(root, 'static/data/giftSpecList.json')],
      }),
      chunk('a/unused.js', {
        facadeModuleId: path.join(root, 'static/data/unused.json'),
        moduleIds: [path.join(root, 'static/data/unused.json')],
      }),
    ]
  }

  it('appends one rule per route with its non-entry chunks and declared spec chunks', async () => {
    const headers = await run(graph())
    const preconnect =
      '  Link: <https://api.example.com>; rel=preconnect; crossorigin=use-credentials'
    expect(headers.endsWith('modulepreload\n')).toBe(true)
    const [existing, list, detail, ...rest] = headers.trimEnd().split('\n\n')

    expect(existing).toBe('/a/*\n  Cache-Control: public, max-age=31536000, immutable')
    expect(list).toBe(`/planner/md/gesellschaft\n${preconnect}\n  Link: </a/list.js>; rel=modulepreload`)
    const [pattern, detailPreconnect, links, ...more] = detail.split('\n')
    expect(pattern).toBe('/planner/md/gesellschaft/:id')
    expect(detailPreconnect).toBe(preconnect)
    expect(more).toEqual([])
    const hinted = [...links.matchAll(/<\/(a\/[^>]+)>; rel=modulepreload/g)].map((m) => m[1])
    expect(hinted[0]).toBe('a/detail.js')
    expect([...hinted].sort()).toEqual([
      'a/catalog.js',
      'a/detail.js',
      'a/gifthook.js',
      'a/giftspec.js',
      'a/viewer.js',
    ])
    expect(rest).toEqual([])
  })

  it('fails the build when a route page cannot be resolved', async () => {
    fs.rmSync(path.join(root, 'src/pages/planner/DetailPage.tsx'))
    await expect(run(graph())).rejects.toThrow(/cannot resolve @\/pages\/planner\/DetailPage/)
  })

  it('fails the build when a route page is in no chunk of the bundle', async () => {
    const withoutDetail = graph().filter((c) => c.fileName !== 'a/detail.js')
    await expect(run(withoutDetail)).rejects.toThrow(/DetailPage.tsx is in no chunk/)
  })
})
