import type { PanicInfo } from '../schemas/PanicInfoSchemas'

export const PANIC_ICON_FALLBACK_ID = 9999

export function panicIconId(panicInfo: PanicInfo, panicType: string): number {
  return panicInfo[panicType]?.iconId ?? PANIC_ICON_FALLBACK_ID
}
