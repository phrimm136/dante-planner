import { z } from 'zod'
import { SEASONS } from '@/shared/gameData'

export const SeasonsI18nSchema = z
  .object(
    Object.fromEntries(SEASONS.map((season) => [String(season), z.string()])) as {
      [K in `${(typeof SEASONS)[number]}`]: z.ZodString
    },
  )
  .strict()
