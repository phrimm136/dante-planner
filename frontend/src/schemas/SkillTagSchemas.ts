import { z } from 'zod'

/**
 * Skill Tag Schemas
 *
 * Zod schemas for runtime validation of skill tag data structures.
 * Skill tags are simple key -> display text mappings for skill/passive descriptions.
 *
 * Example: { "OnSucceedAttack": "[On Hit]", "WinDuel": "[Clash Win]" }
 */

/**
 * SkillTags schema - Record of tag key to display text
 * Simpler than BattleKeywords (no nested object, just strings)
 */
export const SkillTagSchema = z.record(z.string(), z.string())

/**
 * Inferred type for skill tags dictionary
 */
export type SkillTags = z.infer<typeof SkillTagSchema>
