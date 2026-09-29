export type ContentPathSegment =
  | { kind: 'field'; name: string }
  | { kind: 'index'; value: number }
  | { kind: 'key'; name: string }

export type ContentPath = readonly ContentPathSegment[]

export const field = (name: string): ContentPathSegment => ({ kind: 'field', name })

export const index = (value: number): ContentPathSegment => ({ kind: 'index', value })

export const key = (name: string): ContentPathSegment => ({ kind: 'key', name })

export const contentPath = (...segments: ContentPathSegment[]): ContentPath => segments

export const extendPath = (path: ContentPath, ...segments: ContentPathSegment[]): ContentPath => [
  ...path,
  ...segments,
]

const FIELD_SEPARATOR = '.'
const OPEN_BRACKET = '['
const CLOSE_BRACKET = ']'

const renderSegment = (segment: ContentPathSegment, position: number): string => {
  switch (segment.kind) {
    case 'field':
      return position === 0 ? segment.name : FIELD_SEPARATOR + segment.name
    case 'index':
      return OPEN_BRACKET + String(segment.value) + CLOSE_BRACKET
    case 'key':
      return OPEN_BRACKET + segment.name + CLOSE_BRACKET
  }
}

export const renderContentPath = (path: ContentPath): string => path.map(renderSegment).join('')

const KIND_RANK: Record<ContentPathSegment['kind'], number> = { field: 0, index: 1, key: 2 }

const segmentValue = (segment: ContentPathSegment): number | string =>
  segment.kind === 'index' ? segment.value : segment.name

const compareValues = (left: number | string, right: number | string) =>
  left < right ? -1 : left > right ? 1 : 0

function compareSegments(left: ContentPathSegment, right: ContentPathSegment): number {
  const byKind = KIND_RANK[left.kind] - KIND_RANK[right.kind]
  if (byKind !== 0) return Math.sign(byKind)
  return compareValues(segmentValue(left), segmentValue(right))
}

export function compareContentPaths(left: ContentPath, right: ContentPath): number {
  for (const [position, segment] of left.entries()) {
    const other = right[position]
    if (other === undefined) return 1
    const order = compareSegments(segment, other)
    if (order !== 0) return order
  }
  return left.length === right.length ? 0 : -1
}
