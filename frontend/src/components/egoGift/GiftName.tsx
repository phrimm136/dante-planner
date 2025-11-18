interface GiftNameProps {
  name: string
}

export default function GiftName({ name }: GiftNameProps) {
  return <h1 className="text-2xl font-bold">{name}</h1>
}
