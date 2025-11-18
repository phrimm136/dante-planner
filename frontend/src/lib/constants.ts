/**
 * Sin types available in the game
 */
export const SINS = ['Wrath', 'Lust', 'Sloth', 'Gluttony', 'Gloom', 'Pride', 'Envy'] as const

/**
 * Sin type derived from SINS array
 */
export type Sin = typeof SINS[number]
