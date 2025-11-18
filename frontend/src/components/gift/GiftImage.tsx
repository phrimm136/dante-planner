interface GiftImageProps {
  id: string
}

export default function GiftImage({ id }: GiftImageProps) {
  return (
    <div className="border rounded p-4 flex items-center justify-center bg-gray-50">
      <img
        src={`/images/gift/${id}.webp`}
        alt="Gift"
        className="w-32 h-32 object-contain"
      />
    </div>
  )
}
