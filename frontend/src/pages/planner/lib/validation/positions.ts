export const firstIndexOf = <T>(values: readonly T[]): ReadonlyMap<T, number> =>
  values.reduce(
    (first, value, valueIndex) => (first.has(value) ? first : first.set(value, valueIndex)),
    new Map<T, number>(),
  )
