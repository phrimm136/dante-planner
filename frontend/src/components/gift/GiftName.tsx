interface GiftNameProps {
  name: string
}

export default function GiftName({ name }: GiftNameProps) {
  return (
    <div className="border rounded p-4">
      <h1 className="text-3xl font-bold text-center">{name}</h1>
    </div>
  )
}
