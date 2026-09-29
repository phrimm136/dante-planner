import { extendPath, key } from './contentPath'
import type { ContentPath } from './contentPath'

export type SinnerEntry<V> = { sinnerKey: string; path: ContentPath; value: V }

export const sinnerEntries = <V>(
  sinners: Readonly<Record<string, V>>,
  path: ContentPath,
): SinnerEntry<V>[] =>
  Object.entries(sinners).map(([sinnerKey, value]) => ({
    sinnerKey,
    path: extendPath(path, key(sinnerKey)),
    value,
  }))
