import { z } from 'zod'
import { AFFINITIES, EGO_TYPES } from '../constants'

export const AffinitySchema = z.enum(AFFINITIES)

export const EgoTypeSchema = z.enum(EGO_TYPES)
