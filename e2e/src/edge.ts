import { request as playwrightRequest } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { ACCESS_HEADERS } from './staging'

interface EdgeContextOptions {
  baseURL: string
  extraHTTPHeaders?: Record<string, string>
}

/**
 * Builds a request context that can get past Cloudflare Access.
 *
 * `request.newContext` does not inherit `use.extraHTTPHeaders` from the config, so a context
 * created inline carries no service token and is challenged at the edge before it reaches the
 * origin. Every context the suites build directly goes through here; locally the headers are
 * empty and this is indistinguishable from calling newContext.
 */
export function edgeContext(options: EdgeContextOptions): Promise<APIRequestContext> {
  return playwrightRequest.newContext({
    ...options,
    extraHTTPHeaders: { ...ACCESS_HEADERS, ...(options.extraHTTPHeaders ?? {}) },
  })
}
