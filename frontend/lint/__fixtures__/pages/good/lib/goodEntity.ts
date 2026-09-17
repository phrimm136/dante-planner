type GoodId = string & { readonly brand: unique symbol }
type GoodSpec = { readonly rarity: number }
type GoodEntity = { readonly id: GoodId; readonly name: string; readonly spec: GoodSpec }

const idSchema = { parse: (value: string) => value as GoodId }

function createEntityBuilder<TId, TSpec>(schema: { parse: (value: string) => TId }) {
  return (id: string, spec: TSpec, name?: string) => ({
    id: schema.parse(id),
    name: name ?? id,
    spec,
  })
}

export const toGoodEntity = createEntityBuilder<GoodId, GoodSpec>(idSchema)

export function toUnknownGoodEntity(id: string, name: string): GoodEntity {
  return { id: idSchema.parse(id), name, spec: { rarity: 0 } }
}
