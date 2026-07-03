/**
 * Query key factories for entity data hooks.
 *
 * Tuple shapes are load-bearing cache identities — they must stay
 * byte-identical to the keys already stored by existing hooks, or
 * cache lookups and invalidation break silently.
 */

/**
 * Creates the query key factory for an entity LIST domain.
 *
 * Shapes: `[ns, 'list']` / `[ns, 'list', 'spec']` / `[ns, 'list', 'i18n', language]`
 *
 * @param ns - Entity namespace, e.g. `'identity'`
 */
export function createEntityListQueryKeys<Ns extends string>(ns: Ns) {
  return {
    all: () => [ns, 'list'] as const,
    spec: () => [ns, 'list', 'spec'] as const,
    i18n: (language: string) => [ns, 'list', 'i18n', language] as const,
  }
}

/**
 * Creates the query key factory for an entity DETAIL domain.
 *
 * Shapes: `[ns]` / `[ns, id]` / `[ns, id, 'i18n', language]`
 *
 * @param ns - Entity namespace, e.g. `'identity'`
 */
export function createEntityDetailQueryKeys<Ns extends string>(ns: Ns) {
  return {
    all: () => [ns] as const,
    detail: (id: string) => [ns, id] as const,
    i18n: (id: string, language: string) => [ns, id, 'i18n', language] as const,
  }
}
