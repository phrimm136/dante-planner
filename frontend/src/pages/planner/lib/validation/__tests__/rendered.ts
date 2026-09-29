import { renderContentPath } from '../contentPath'
import type { ContentPath } from '../contentPath'

export const rendered = <V extends { path: ContentPath }>(violations: readonly V[]) =>
  violations.map((violation) => ({ ...violation, path: renderContentPath(violation.path) }))
