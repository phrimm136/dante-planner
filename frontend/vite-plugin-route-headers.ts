import fs from 'fs'
import path from 'path'
import { parseAst } from 'rolldown/parseAst'
import type { OutputBundle } from 'rolldown'
import type { Plugin } from 'vite'

const MAX_RULES = 100
const MAX_LINE_LENGTH = 2000
const DATA_SPECIFIER_PREFIX = '@static/data/'
const I18N_MODULE_SEGMENT = '/static/i18n/'
const HEADER_INDENT = '  '

interface AstNode {
  type: string
  [key: string]: unknown
}

export interface RouteSource {
  path: string
  page: string
  loader: string | null
}

export interface ChunkNode {
  fileName: string
  isEntry: boolean
  facadeModuleId: string | null
  moduleIds: string[]
  imports: string[]
}

export interface ResolvedRoute {
  path: string
  pageModule: string
  loaderModules: string[]
  dataModules: string[]
}

interface RouteHeadersOptions {
  routerFile: string
  loadersFile: string
}

function fail(message: string): never {
  throw new Error(`[route-headers] ${message}`)
}

function isNode(value: unknown): value is AstNode {
  return typeof value === 'object' && value !== null && typeof (value as AstNode).type === 'string'
}

function walk(node: AstNode, visit: (node: AstNode) => void): void {
  visit(node)
  for (const value of Object.values(node)) {
    if (Array.isArray(value)) {
      for (const item of value) if (isNode(item)) walk(item, visit)
    } else if (isNode(value)) {
      walk(value, visit)
    }
  }
}

function parse(source: string, fileName: string): AstNode {
  const lang = fileName.endsWith('.tsx') ? 'tsx' : 'ts'
  return parseAst(source, { lang }, fileName) as unknown as AstNode
}

function stringLiteral(node: unknown): string | null {
  return isNode(node) && node.type === 'Literal' && typeof node.value === 'string'
    ? node.value
    : null
}

function identifierName(node: unknown): string | null {
  return isNode(node) && node.type === 'Identifier' ? (node.name as string) : null
}

function property(object: AstNode, key: string): unknown {
  const properties = object.properties as AstNode[]
  const match = properties.find((p) => p.type === 'Property' && identifierName(p.key) === key)
  return match?.value
}

function lazyImportSpecifier(component: unknown): string | null {
  if (!isNode(component) || component.type !== 'CallExpression') return null
  if (identifierName(component.callee) !== 'lazyRouteComponent') return null
  const [thunk] = component.arguments as unknown[]
  if (!isNode(thunk) || thunk.type !== 'ArrowFunctionExpression') return null
  const body = thunk.body
  if (!isNode(body) || body.type !== 'ImportExpression') return null
  return stringLiteral(body.source)
}

export function parseRouteTable(source: string, fileName = 'router.tsx'): RouteSource[] {
  const program = parse(source, fileName)
  const rootNames = new Set<string>()
  walk(program, (node) => {
    if (node.type !== 'VariableDeclarator' || !isNode(node.init)) return
    if (
      node.init.type === 'CallExpression' &&
      identifierName(node.init.callee) === 'createRootRoute'
    ) {
      const name = identifierName(node.id)
      if (name) rootNames.add(name)
    }
  })

  const routes: RouteSource[] = []
  walk(program, (node) => {
    if (node.type !== 'CallExpression' || identifierName(node.callee) !== 'createRoute') return
    const [options] = node.arguments as unknown[]
    if (!isNode(options) || options.type !== 'ObjectExpression') {
      fail('createRoute is called without an object literal')
    }
    const routePath = stringLiteral(property(options, 'path'))
    if (routePath === null) fail('a createRoute call has no string-literal path')

    const parent = property(options, 'getParentRoute')
    const parentName =
      isNode(parent) && parent.type === 'ArrowFunctionExpression'
        ? identifierName(parent.body)
        : null
    if (parentName === null || !rootNames.has(parentName)) {
      fail(`route ${routePath} is not a direct child of the root route`)
    }

    const page = lazyImportSpecifier(property(options, 'component'))
    if (page === null) {
      fail(`route ${routePath} has no lazyRouteComponent(() => import('<literal>')) component`)
    }

    const loaderNode = property(options, 'loader')
    const loader = loaderNode === undefined ? null : identifierName(loaderNode)
    if (loaderNode !== undefined && loader === null) {
      fail(`route ${routePath} has a loader that is not a named function`)
    }

    routes.push({ path: routePath, page, loader })
  })

  if (routes.length === 0) fail('no createRoute calls found')
  return routes
}

interface FunctionFacts {
  imports: string[]
  calls: string[]
}

function topLevelFunctions(program: AstNode): Map<string, FunctionFacts> {
  const functions = new Map<string, FunctionFacts>()
  for (const statement of program.body as AstNode[]) {
    const declaration =
      statement.type === 'ExportNamedDeclaration' && isNode(statement.declaration)
        ? statement.declaration
        : statement
    if (declaration.type !== 'FunctionDeclaration') continue
    const name = identifierName(declaration.id)
    if (name === null) continue
    const facts: FunctionFacts = { imports: [], calls: [] }
    walk(declaration.body as AstNode, (node) => {
      if (node.type === 'ImportExpression') {
        const specifier = stringLiteral(node.source)
        if (specifier !== null) facts.imports.push(specifier)
      } else if (node.type === 'CallExpression') {
        const callee = identifierName(node.callee)
        if (callee !== null) facts.calls.push(callee)
      }
    })
    functions.set(name, facts)
  }
  return functions
}

export function loaderImports(
  source: string,
  loader: string,
  fileName = 'routeLoaders.ts',
): string[] {
  const functions = topLevelFunctions(parse(source, fileName))
  if (!functions.has(loader)) fail(`loader ${loader} is not a top-level function in ${fileName}`)

  const specifiers = new Set<string>()
  const seen = new Set<string>()
  const pending = [loader]
  while (pending.length > 0) {
    const name = pending.pop() as string
    if (seen.has(name)) continue
    seen.add(name)
    const facts = functions.get(name)
    if (facts === undefined) continue
    for (const specifier of facts.imports) specifiers.add(specifier)
    pending.push(...facts.calls)
  }
  return [...specifiers]
}

export function literalDataImports(source: string, fileName: string): string[] {
  const specifiers = new Set<string>()
  walk(parse(source, fileName), (node) => {
    if (node.type !== 'ImportExpression') return
    const specifier = stringLiteral(node.source)
    if (specifier?.startsWith(DATA_SPECIFIER_PREFIX)) specifiers.add(specifier)
  })
  return [...specifiers]
}

export function toPagesPattern(routePath: string): string {
  return routePath
    .split('/')
    .map((segment) => {
      if (!segment.startsWith('$')) return segment
      const name = segment.slice(1)
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) {
        fail(`route ${routePath} has a segment ${segment} that has no Pages placeholder form`)
      }
      return `:${name}`
    })
    .join('/')
}

function staticClosure(chunks: Map<string, ChunkNode>, start: string, into: Set<string>): void {
  if (into.has(start)) return
  const chunk = chunks.get(start)
  if (chunk === undefined) fail(`chunk ${start} is imported but not in the bundle`)
  into.add(start)
  for (const imported of chunk.imports) staticClosure(chunks, imported, into)
}

function chunkOf(chunks: Map<string, ChunkNode>, moduleId: string): ChunkNode {
  const all = [...chunks.values()]
  const chunk =
    all.find((c) => c.facadeModuleId === moduleId) ??
    all.find((c) => c.moduleIds.includes(moduleId))
  if (chunk === undefined) fail(`module ${moduleId} is in no chunk`)
  return chunk
}

export function routeLoadSet(chunkList: ChunkNode[], route: ResolvedRoute): string[] {
  const chunks = new Map(chunkList.map((c) => [c.fileName, c]))
  const entry = new Set<string>()
  for (const chunk of chunkList) if (chunk.isEntry) staticClosure(chunks, chunk.fileName, entry)

  const reached = new Set<string>()
  for (const moduleId of [route.pageModule, ...route.loaderModules]) {
    staticClosure(chunks, chunkOf(chunks, moduleId).fileName, reached)
  }
  for (const moduleId of route.dataModules) reached.add(chunkOf(chunks, moduleId).fileName)

  const files = [...reached].filter((fileName) => !entry.has(fileName))
  for (const fileName of files) {
    const i18nModule = (chunks.get(fileName) as ChunkNode).moduleIds.find((id) =>
      id.includes(I18N_MODULE_SEGMENT),
    )
    if (i18nModule !== undefined) {
      fail(`route ${route.path} would hint ${fileName}, which holds per-language ${i18nModule}`)
    }
  }
  return files
}

function linkLines(values: string[]): string[] {
  const prefix = `${HEADER_INDENT}Link: `
  const lines: string[] = []
  let current: string[] = []
  for (const value of values) {
    if (prefix.length + value.length > MAX_LINE_LENGTH) fail(`Link value ${value} exceeds one line`)
    const candidate = [...current, value]
    if (current.length > 0 && prefix.length + candidate.join(', ').length > MAX_LINE_LENGTH) {
      lines.push(prefix + current.join(', '))
      current = [value]
    } else {
      current = candidate
    }
  }
  if (current.length > 0) lines.push(prefix + current.join(', '))
  return lines
}

export function formatRule(pattern: string, files: string[], apiOrigin: string | null): string {
  const lines = [pattern]
  if (apiOrigin !== null) {
    lines.push(`${HEADER_INDENT}Link: <${apiOrigin}>; rel=preconnect; crossorigin=use-credentials`)
  }
  lines.push(...linkLines(files.map((file) => `</${file}>; rel=modulepreload`)))
  return lines.join('\n')
}

export function countRules(headers: string): number {
  return headers.split('\n').filter((line) => line.length > 0 && !/^[\s#]/.test(line)).length
}

export function apiOriginOf(baseUrl: string | undefined): string | null {
  const trimmed = baseUrl?.trim()
  if (!trimmed) return null
  return new URL(trimmed).origin
}

export function appendRules(existing: string, rules: string[]): string {
  const total = countRules(existing) + rules.length
  if (total > MAX_RULES) fail(`_headers would hold ${total} rules; Pages allows ${MAX_RULES}`)
  const base = existing.endsWith('\n') ? existing : `${existing}\n`
  return `${base}\n${rules.join('\n\n')}\n`
}

function chunkNodes(bundle: OutputBundle): ChunkNode[] {
  return Object.values(bundle).flatMap((output) =>
    output.type === 'chunk'
      ? [
          {
            fileName: output.fileName,
            isEntry: output.isEntry,
            facadeModuleId: output.facadeModuleId,
            moduleIds: output.moduleIds,
            imports: output.imports,
          },
        ]
      : [],
  )
}

export function routeHeadersPlugin(options: RouteHeadersOptions): Plugin {
  let outDir = ''
  let apiOrigin: string | null = null
  let rules: string[] | null = null

  return {
    name: 'route-headers',
    apply: 'build',

    configResolved(config) {
      outDir = path.resolve(config.root, config.build.outDir)
      apiOrigin = apiOriginOf(config.env.VITE_API_BASE_URL as string | undefined)
    },

    async generateBundle(_output, bundle) {
      const resolve = async (specifier: string, importer: string): Promise<string> => {
        const resolved = await this.resolve(specifier, importer)
        if (resolved === null) fail(`cannot resolve ${specifier} from ${importer}`)
        return resolved.id
      }

      const routerSource = await fs.promises.readFile(options.routerFile, 'utf8')
      const loadersSource = await fs.promises.readFile(options.loadersFile, 'utf8')
      const chunks = chunkNodes(bundle)

      const resolvedRoutes: ResolvedRoute[] = []
      for (const route of parseRouteTable(routerSource, options.routerFile)) {
        const loaderSpecifiers =
          route.loader === null
            ? []
            : loaderImports(loadersSource, route.loader, options.loadersFile)
        const loaderModules = await Promise.all(
          loaderSpecifiers.map((specifier) => resolve(specifier, options.loadersFile)),
        )
        const dataModules: string[] = []
        for (const moduleId of loaderModules) {
          const source = await fs.promises.readFile(moduleId, 'utf8')
          for (const specifier of literalDataImports(source, moduleId)) {
            dataModules.push(await resolve(specifier, moduleId))
          }
        }
        resolvedRoutes.push({
          path: route.path,
          pageModule: await resolve(route.page, options.routerFile),
          loaderModules,
          dataModules,
        })
      }

      rules = resolvedRoutes.map((route) =>
        formatRule(toPagesPattern(route.path), routeLoadSet(chunks, route), apiOrigin),
      )
    },

    async closeBundle() {
      if (rules === null) return
      const headersFile = path.join(outDir, '_headers')
      const existing = await fs.promises.readFile(headersFile, 'utf8')
      await fs.promises.writeFile(headersFile, appendRules(existing, rules))
    },
  }
}
