import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import keywordMatch from '@static/i18n/EN/keywordMatch.json'
import i18n from './i18n'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function assertNever(value: never): never {
  throw new Error(`Unhandled union member: ${JSON.stringify(value)}`)
}

/**
 * Object.entries that keeps a branded/literal key type. Sound only for records
 * whose keys were validated to K at the boundary (e.g. zod record schemas) —
 * Object.entries itself always widens keys to string.
 */
export function typedEntries<K extends string, V>(record: Partial<Record<K, V>>): [K, V][] {
  return Object.entries(record) as [K, V][]
}

export function getSinnerCodeFromId(id: string): string {
  return String(parseInt(id.substring(1, 3), 10))
}

export function getKeywordDisplayName(keyword: string): string {
  return keywordMatch[keyword as keyof typeof keywordMatch] || keyword.toLowerCase()
}

export function calculateByteLength(str: string | null | undefined): number {
  if (!str) return 0
  return new TextEncoder().encode(str).length
}

export function getDisplayFontForLanguage(language?: string): React.CSSProperties {
  const lang = language ?? i18n.language

  switch (lang) {
    case 'KR':
      return { fontFamily: 'var(--font-kotra)' }
    case 'EN':
      return { fontFamily: 'var(--font-mikodacs)' }
    case 'JP':
      return { fontFamily: 'var(--font-corporate)' }
    case 'CN':
      return { fontFamily: 'var(--font-chinese)' }
    default:
      return { fontFamily: 'var(--font-pretendard)' }
  }
}

export function getDisplayFontForNumeric(): string {
  return 'var(--font-excelsior)'
}

export function getDisplayFontForLabel(): string {
  return 'var(--font-bebas)'
}

export function getLineHeightForLanguage(language?: string): number {
  const lang = language ?? i18n.language

  switch (lang) {
    case 'KR':
      return 1.3
    case 'CN':
      return 1.1
    case 'JP':
      return 1.1
    case 'EN':
      return 1.0
    default:
      return 1.2
  }
}

export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}
