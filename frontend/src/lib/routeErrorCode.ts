const CHUNK_LOAD_MARK = Symbol('chunkLoad')

export type RouteErrorCode = 'CHUNK_LOAD' | 'UNKNOWN'

export function markChunkLoadError(error: unknown): void {
  if (typeof error === 'object' && error !== null) {
    Reflect.set(error, CHUNK_LOAD_MARK, true)
  }
}

export function classifyRouteError(error: unknown): RouteErrorCode {
  return typeof error === 'object' && error !== null && Reflect.get(error, CHUNK_LOAD_MARK) === true
    ? 'CHUNK_LOAD'
    : 'UNKNOWN'
}

export function routeErrorLabel(error: unknown): string {
  const code = classifyRouteError(error)
  if (code === 'CHUNK_LOAD' || !(error instanceof Error)) return code
  return `${code} · ${error.name}`
}
