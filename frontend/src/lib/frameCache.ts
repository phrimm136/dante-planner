/**
 * Pre-rendered frame cache for skill images
 * Generates all sin-colored frames on initialization to avoid runtime performance issues
 */

import { SIN_COLORS, type SinType } from './globalConstants'
import { multiplyImageColor } from './utils'

// Cache structure: Map<cacheKey, dataUrl>
const frameCache = new Map<string, string>()

// Track initialization status
let isInitialized = false
let initPromise: Promise<void> | null = null

/**
 * Generate cache key for a frame
 */
function getCacheKey(framePath: string, color: string): string {
  return `${framePath}|${color}`
}

/**
 * Pre-render all possible sin frame combinations
 * This runs once at app startup to avoid runtime performance issues
 */
export async function initializeFrameCache(): Promise<void> {
  // Return existing promise if already initializing
  if (initPromise) return initPromise

  // Return immediately if already initialized
  if (isInitialized) return Promise.resolve()

  initPromise = (async () => {
    const sins: SinType[] = ['wrath', 'lust', 'sloth', 'gluttony', 'gloom', 'pride', 'envy']
    const defense: SinType = 'defense'
    const frameLevels = [1, 2, 3]

    const preRenderTasks: Promise<void>[] = []

    // Pre-render offensive sin frames (all 3 levels)
    for (const sin of sins) {
      const colors = SIN_COLORS[sin]

      for (const level of frameLevels) {
        const frameBGPath = `/images/UI/skillFrame/${sin}${level}BG.webp`
        const framePath = `/images/UI/skillFrame/${sin}${level}.webp`

        // Pre-render background with bg color
        preRenderTasks.push(
          multiplyImageColor(frameBGPath, colors.bg)
            .then((dataUrl) => {
              frameCache.set(getCacheKey(frameBGPath, colors.bg), dataUrl)
            })
            .catch((err) => console.warn(`Failed to pre-render ${frameBGPath}:`, err))
        )

        // Pre-render frame with fg color
        preRenderTasks.push(
          multiplyImageColor(framePath, colors.fg)
            .then((dataUrl) => {
              frameCache.set(getCacheKey(framePath, colors.fg), dataUrl)
            })
            .catch((err) => console.warn(`Failed to pre-render ${framePath}:`, err))
        )
      }
    }

    // Pre-render defense frames (only level 1)
    const colors = SIN_COLORS[defense]
    const level = 1

    const frameBGPath = `/images/UI/skillFrame/${defense}${level}BG.webp`
    const framePath = `/images/UI/skillFrame/${defense}${level}.webp`

    // Pre-render background with bg color
    preRenderTasks.push(
      multiplyImageColor(frameBGPath, colors.bg)
        .then((dataUrl) => {
          frameCache.set(getCacheKey(frameBGPath, colors.bg), dataUrl)
        })
        .catch((err) => console.warn(`Failed to pre-render ${frameBGPath}:`, err))
    )

    // Pre-render frame with fg color
    preRenderTasks.push(
      multiplyImageColor(framePath, colors.fg)
        .then((dataUrl) => {
          frameCache.set(getCacheKey(framePath, colors.fg), dataUrl)
        })
        .catch((err) => console.warn(`Failed to pre-render ${framePath}:`, err))
    )

    // Pre-render attack type frames for all sins 
    const attackFrameBGPath = '/images/UI/skillFrame/attackTypeBG.webp'
    const attackFramePath = '/images/UI/skillFrame/attackType.webp'

    for (const sin of sins) {
      const colors = SIN_COLORS[sin]

      preRenderTasks.push(
        multiplyImageColor(attackFrameBGPath, colors.bg)
          .then((dataUrl) => {
            frameCache.set(getCacheKey(attackFrameBGPath, colors.bg), dataUrl)
          })
          .catch((err) => console.warn(`Failed to pre-render attack BG for ${sin}:`, err))
      )

      preRenderTasks.push(
        multiplyImageColor(attackFramePath, colors.fg)
          .then((dataUrl) => {
            frameCache.set(getCacheKey(attackFramePath, colors.fg), dataUrl)
          })
          .catch((err) => console.warn(`Failed to pre-render attack frame for ${sin}:`, err))
      )
    }

    // Wait for all pre-rendering to complete
    await Promise.all(preRenderTasks)

    isInitialized = true
    console.log(`Frame cache initialized: ${frameCache.size} frames pre-rendered`)
  })()

  return initPromise
}

/**
 * Get a pre-rendered colored frame from cache
 * @param framePath - Path to the frame image
 * @param hexColor - Hex color to multiply with
 * @returns Data URL of the colored frame, or null if not cached
 */
export function getCachedFrame(framePath: string, hexColor: string): string | null {
  return frameCache.get(getCacheKey(framePath, hexColor)) || null
}

/**
 * Check if frame cache is initialized
 */
export function isFrameCacheInitialized(): boolean {
  return isInitialized
}
