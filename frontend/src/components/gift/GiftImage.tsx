interface GiftImageProps {
  id: string
}

export default function GiftImage({ id }: GiftImageProps) {
  return (
    <div className="flex items-center justify-center">
      <img
        src={`/images/egoGift/${id}.webp`}
        alt="Gift"
        className="w-32 h-32 object-contain"
      />
    </div>
  )
}
