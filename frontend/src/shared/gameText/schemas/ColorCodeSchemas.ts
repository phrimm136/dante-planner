import { z } from 'zod'
import { HexColorSchema } from '@/lib/colorUtils'

export const ColorCodeMapSchema = z.record(z.string(), HexColorSchema)

export type ColorCodeMap = z.infer<typeof ColorCodeMapSchema>
