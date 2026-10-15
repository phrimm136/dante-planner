import { getStartBuffSpritePath } from '@/shared/assets'
import type { MDVersion } from '@/shared/gameData'
import type { LayoutImage, UnitRect } from '../../schemas/StartBuffCardSchemas'
import { imageStyle, type LayoutSpace } from '../../lib/startBuffCardLayout'

interface CardSpriteProps {
  version: MDVersion
  file: string
  image: LayoutImage
  rect: UnitRect
  space: LayoutSpace
}

export function CardSprite({ version, file, image, rect, space }: CardSpriteProps) {
  const src = getStartBuffSpritePath(version, file)
  const style = { ...imageStyle(image, rect, space, src), pointerEvents: 'none' as const }

  if (image.slice !== null) return <div data-sprite={src} style={style} />
  return <img src={src} alt="" style={style} />
}
