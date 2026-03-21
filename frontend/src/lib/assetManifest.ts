import manifest from 'virtual:asset-manifest'
import { hashKey } from './hashKey'

export function resolveAsset(path: string): string {
  const key = path.startsWith('/') ? path.slice(1) : path
  const hashed = (manifest as Record<string, string>)[hashKey(key)]
  return hashed ? `/${hashed}` : path
}
