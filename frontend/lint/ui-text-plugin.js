import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { z } from 'zod'

const FRONTEND_ROOT = fileURLToPath(new URL('..', import.meta.url))
const BASELINE_HINT = 'lint/ui-text-baseline.json'

const RESTRICTED_ATTRIBUTES = new Set([
  'placeholder',
  'title',
  'alt',
  'aria-label',
  'label',
  'description',
  'helperText',
  'errorMessage',
  'emptyMessage',
  'loadingText',
  'confirmText',
  'cancelText',
  'emptyStateFallback',
  'titleFallback',
])
const OBJECT_KEYS = new Set(['label', 'title', 'description', 'placeholder'])
const DEFAULT_PROP_NAME = /^(?:text|label|message|title)$|(?:Text|Label|Message|Title)$/
const FUNCTION_TYPES = new Set([
  'FunctionDeclaration',
  'FunctionExpression',
  'ArrowFunctionExpression',
])

const LETTER = /\p{L}/u
const I18N_KEY = /^[A-Za-z0-9_]+(?:[.:][A-Za-z0-9_]+)+$/
const URL_TEXT = /^[a-z]+:\/\//
const CLASS_TOKEN = /^[!a-z0-9:[\]/.%#-]+$/

const normalize = (text) => text.replace(/\s+/g, ' ').trim()

const isClassList = (text) =>
  !text.includes('/') &&
  /[-:]/.test(text) &&
  text.split(' ').every((token) => CLASS_TOKEN.test(token))

const isExempt = (text, allowedStrings) =>
  !LETTER.test(text) ||
  allowedStrings.has(text) ||
  I18N_KEY.test(text) ||
  URL_TEXT.test(text) ||
  isClassList(text)

const toPosix = (file) => file.split(path.sep).join('/')

const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

function baselineSchema(baselineOption) {
  const baselineDir = path.posix.dirname(toPosix(baselineOption))
  const filePath = z.string().regex(new RegExp(`^(?:src|${escapeRegExp(baselineDir)})/.+\\.tsx?$`))
  const uiText = z.string().min(1).regex(LETTER)
  return z.record(filePath, z.record(uiText, z.number().int().positive()))
}

const baselineCache = new Map()

function loadBaseline(baselineOption) {
  const cached = baselineCache.get(baselineOption)
  if (cached) return cached
  const absolute = path.resolve(FRONTEND_ROOT, baselineOption)
  const result = baselineSchema(baselineOption).safeParse(
    JSON.parse(readFileSync(absolute, 'utf8')),
  )
  if (!result.success) {
    throw new Error(
      `ui-text baseline ${baselineOption} is invalid: ${z.prettifyError(result.error)}`,
    )
  }
  baselineCache.set(baselineOption, result.data)
  return result.data
}

const attributeName = (attribute) =>
  attribute.name.type === 'JSXNamespacedName'
    ? `${attribute.name.namespace.name}:${attribute.name.name.name}`
    : attribute.name.name

const propertyKeyName = (key) => {
  if (key.type === 'Identifier') return key.name
  if (key.type === 'Literal' && typeof key.value === 'string') return key.value
  return null
}

const isDestructuredParameterDefault = (pattern, child) => {
  const property = pattern.parent
  if (pattern.right !== child || property?.type !== 'Property' || property.computed) return false
  const objectPattern = property.parent
  if (objectPattern?.type !== 'ObjectPattern') return false
  const fn = objectPattern.parent
  if (!fn || !FUNCTION_TYPES.has(fn.type) || !fn.params.includes(objectPattern)) return false
  const name = propertyKeyName(property.key)
  return name !== null && (RESTRICTED_ATTRIBUTES.has(name) || DEFAULT_PROP_NAME.test(name))
}

function reachesSink(node) {
  let child = node
  let parent = node.parent
  while (parent) {
    switch (parent.type) {
      case 'ConditionalExpression':
        if (parent.test === child) return false
        break
      case 'LogicalExpression':
        if (parent.operator === '&&' && parent.left === child) return false
        break
      case 'TSAsExpression':
      case 'TSSatisfiesExpression':
      case 'ParenthesizedExpression':
        break
      case 'JSXExpressionContainer':
        return parent.parent?.type === 'JSXAttribute'
          ? RESTRICTED_ATTRIBUTES.has(attributeName(parent.parent))
          : true
      case 'JSXAttribute':
        return RESTRICTED_ATTRIBUTES.has(attributeName(parent))
      case 'Property':
        return (
          parent.value === child &&
          !parent.computed &&
          parent.parent?.type === 'ObjectExpression' &&
          OBJECT_KEYS.has(propertyKeyName(parent.key))
        )
      case 'AssignmentPattern':
        return isDestructuredParameterDefault(parent, child)
      default:
        return false
    }
    child = parent
    parent = parent.parent
  }
  return false
}

const templateText = (node) => node.quasis.map((quasi) => quasi.value.cooked ?? '').join('${}')

const newTextMessage = (text, found, allowed) =>
  `Hardcoded UI text "${text}": ${found} found, baseline allows ${allowed}. Translate it with t(); never add it to ${BASELINE_HINT}.`

const staleMessage = (file, text, allowed, found) =>
  `Stale baseline entry ${file} → "${text}": baseline allows ${allowed}, found ${found}. Lower or remove it in ${BASELINE_HINT}.`

const noHardcodedText = {
  meta: {
    type: 'problem',
    schema: [
      {
        type: 'object',
        properties: {
          baseline: { type: 'string' },
          allowedStrings: { type: 'array', items: { type: 'string' } },
        },
        required: ['baseline'],
        additionalProperties: false,
      },
    ],
  },
  create(context) {
    const { baseline: baselineOption, allowedStrings = [] } = context.options[0]
    const baseline = loadBaseline(baselineOption)
    const allowed = new Set(allowedStrings)
    const file = toPosix(path.relative(FRONTEND_ROOT, context.filename))
    const occurrences = new Map()

    const collect = (node, rawText) => {
      const text = normalize(rawText)
      if (isExempt(text, allowed)) return
      const nodes = occurrences.get(text)
      if (nodes) nodes.push(node)
      else occurrences.set(text, [node])
    }

    return {
      JSXText(node) {
        collect(node, node.value)
      },
      Literal(node) {
        if (typeof node.value === 'string' && reachesSink(node)) collect(node, node.value)
      },
      TemplateLiteral(node) {
        if (reachesSink(node)) collect(node, templateText(node))
      },
      'Program:exit'(program) {
        const fileBaseline = baseline[file] ?? {}
        for (const [text, nodes] of occurrences) {
          const allowedCount = fileBaseline[text] ?? 0
          for (const node of nodes.slice(allowedCount)) {
            context.report({ node, message: newTextMessage(text, nodes.length, allowedCount) })
          }
        }
        for (const [text, allowedCount] of Object.entries(fileBaseline)) {
          const found = occurrences.get(text)?.length ?? 0
          if (found < allowedCount) {
            context.report({
              node: program,
              message: staleMessage(file, text, allowedCount, found),
            })
          }
        }
      },
    }
  },
}

export default {
  meta: { name: 'ui-text' },
  rules: {
    'no-hardcoded-text': noHardcodedText,
  },
}
