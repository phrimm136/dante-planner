import { z } from 'zod'
import { AB_PART_TYPES, AFFINITIES, EGO_TYPES } from '../constants'

export const AffinitySchema = z.enum(AFFINITIES)

export const EgoTypeSchema = z.enum(EGO_TYPES)

export const AbPartTypeSchema = z.enum(AB_PART_TYPES)
