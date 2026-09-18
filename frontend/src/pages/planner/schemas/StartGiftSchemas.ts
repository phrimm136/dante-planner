import { z } from 'zod'
import { EGOGiftIdSchema } from '@/shared/gameData'

export const StartEgoGiftPoolsSchema = z.record(z.string(), z.array(EGOGiftIdSchema))
