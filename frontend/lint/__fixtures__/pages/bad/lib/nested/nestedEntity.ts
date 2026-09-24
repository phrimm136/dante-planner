type BadId = string & { readonly brand: unique symbol }
type BadSpec = { readonly rarity: number }

const idSchema = { parse: (value: string) => value as BadId }

export const toBadEntities = (specs: Record<string, BadSpec>) =>
  Object.entries(specs).map(([id, spec]) => ({ id: idSchema.parse(id), spec }))

export const toBadEntity = (id: string, spec: BadSpec) => ({ id: idSchema.parse(id), spec })

export function helper(id: string): BadId {
  return idSchema.parse(id)
}
