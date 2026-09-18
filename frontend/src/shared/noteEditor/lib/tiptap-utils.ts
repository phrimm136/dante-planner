import { Selection, TextSelection } from '@tiptap/pm/state'
import { type Editor } from '@tiptap/react'

const MAC_SYMBOLS: Record<string, string> = {
  mod: '⌘',
  command: '⌘',
  meta: '⌘',
  ctrl: '⌃',
  control: '⌃',
  alt: '⌥',
  option: '⌥',
  shift: '⇧',
  backspace: 'Del',
  delete: '⌦',
  enter: '⏎',
  escape: '⎋',
  capslock: '⇪',
} as const

function isMac(): boolean {
  return typeof navigator !== 'undefined' && navigator.platform.toLowerCase().includes('mac')
}

const formatShortcutKey = (key: string, isMac: boolean, capitalize: boolean = true) => {
  if (isMac) {
    const lowerKey = key.toLowerCase()
    return MAC_SYMBOLS[lowerKey] || (capitalize ? key.toUpperCase() : key)
  }

  return capitalize ? key.charAt(0).toUpperCase() + key.slice(1) : key
}

export const parseShortcutKeys = (props: {
  shortcutKeys: string | undefined
  delimiter?: string
  capitalize?: boolean
}) => {
  const { shortcutKeys, delimiter = '+', capitalize = true } = props

  if (!shortcutKeys) return []

  return shortcutKeys
    .split(delimiter)
    .map((key) => key.trim())
    .map((key) => formatShortcutKey(key, isMac(), capitalize))
}

export function focusNextNode(editor: Editor) {
  const { state, view } = editor
  const { doc, selection } = state

  const nextSel = Selection.findFrom(selection.$to, 1, true)
  if (nextSel) {
    view.dispatch(state.tr.setSelection(nextSel).scrollIntoView())
    return true
  }

  const paragraphType = state.schema.nodes.paragraph
  if (!paragraphType) {
    console.warn('No paragraph node type found in schema.')
    return false
  }

  const end = doc.content.size
  const para = paragraphType.create()
  let tr = state.tr.insert(end, para)

  const $inside = tr.doc.resolve(end + 1)
  tr = tr.setSelection(TextSelection.near($inside)).scrollIntoView()
  view.dispatch(tr)
  return true
}

export function isValidPosition(pos: number | null | undefined): pos is number {
  return typeof pos === 'number' && pos >= 0
}

export function isExtensionAvailable(
  editor: Editor | null,
  extensionNames: string | string[],
): boolean {
  if (!editor) return false

  const names = Array.isArray(extensionNames) ? extensionNames : [extensionNames]

  const found = names.some((name) =>
    editor.extensionManager.extensions.some((ext) => ext.name === name),
  )

  if (!found) {
    console.warn(
      `None of the extensions [${names.join(', ')}] were found in the editor schema. Ensure they are included in the editor configuration.`,
    )
  }

  return found
}

type ProtocolOptions = {
  scheme: string

  optionalSlashes?: boolean
}

type ProtocolConfig = Array<ProtocolOptions | string>

const ATTR_WHITESPACE =
  // eslint-disable-next-line no-control-regex
  /[\u0000-\u0020\u00A0\u1680\u180E\u2000-\u2029\u205F\u3000]/g

function isAllowedUri(uri: string | undefined, protocols?: ProtocolConfig) {
  const allowedProtocols: string[] = [
    'http',
    'https',
    'ftp',
    'ftps',
    'mailto',
    'tel',
    'callto',
    'sms',
    'cid',
    'xmpp',
  ]

  if (protocols) {
    protocols.forEach((protocol) => {
      const nextProtocol = typeof protocol === 'string' ? protocol : protocol.scheme

      if (nextProtocol) {
        allowedProtocols.push(nextProtocol)
      }
    })
  }

  return (
    !uri ||
    uri.replace(ATTR_WHITESPACE, '').match(
      new RegExp(
        // eslint-disable-next-line no-useless-escape
        `^(?:(?:${allowedProtocols.join('|')}):|[^a-z]|[a-z0-9+.\-]+(?:[^a-z+.\-:]|$))`,
        'i',
      ),
    )
  )
}

export function sanitizeUrl(inputUrl: string, baseUrl: string, protocols?: ProtocolConfig): string {
  try {
    const url = new URL(inputUrl, baseUrl)

    if (isAllowedUri(url.href, protocols)) {
      return url.href
    }
  } catch {
    // If URL creation fails, it's considered invalid
  }
  return '#'
}
