#!/usr/bin/env node
/**
 * Detects hardcoded English plain text in frontend/src that should be i18n'd.
 *
 * Usage: yarn tsx scripts/detect-hardcoded-text.ts
 *
 * Detects:
 * - JSX text content (children of elements)
 * - String props: placeholder, title, alt, aria-label, label
 * - Template literals with user-visible text
 *
 * Excludes:
 * - Already translated text (wrapped in t())
 * - Imports, exports, type definitions
 * - CSS classes, technical identifiers
 * - Console logs, comments
 * - URLs, file paths, asset paths
 * - Test files
 */

import * as fs from 'fs'
import * as path from 'path'
import * as ts from 'typescript'
import { fileURLToPath } from 'url'
import { dirname } from 'path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

type Severity = 'high' | 'medium' | 'low'

interface Detection {
  file: string
  line: number
  column: number
  type: 'jsx-text' | 'string-prop' | 'string-literal'
  text: string
  context: string
  severity: Severity
}

const SRC_DIR = path.resolve(__dirname, '../src')

// Props that contain user-visible text
const USER_VISIBLE_PROPS = new Set([
  'placeholder',
  'title',
  'alt',
  'aria-label',
  'aria-describedby',
  'aria-labelledby',
  'label',
  'description',
  'helperText',
  'errorMessage',
  'emptyMessage',
  'loadingText',
  'confirmText',
  'cancelText',
])

// Patterns to exclude (technical, not user-visible)
const EXCLUDE_PATTERNS = [
  /^[a-z][a-zA-Z0-9]*$/, // camelCase identifiers
  /^[A-Z][A-Z0-9_]*$/, // CONSTANT_CASE
  /^[A-Z][a-zA-Z0-9]*$/, // PascalCase (component names)
  /^https?:\/\//, // URLs
  /^\/[a-zA-Z]/, // File paths starting with /
  /^\.[a-zA-Z]/, // Relative paths
  /^@\//, // Import aliases
  /^[a-z]+:/, // Protocols like mailto:, tel:
  /^#[0-9a-fA-F]{3,8}$/, // Hex colors
  /^rgb/, // RGB colors
  /^hsl/, // HSL colors
  /^\d+(\.\d+)?(px|em|rem|%|vh|vw|s|ms)?$/, // CSS units
  /^[0-9.]+$/, // Pure numbers
  /^\s*$/, // Whitespace only
  /^[a-z]+-[a-z-]+$/, // kebab-case (CSS classes)
  /^[a-z]+_[a-z_]+$/, // snake_case
  /^data-/, // Data attributes
  /^on[A-Z]/, // Event handlers
  /^\{.*\}$/, // Template expressions
  /^[<>{}[\]()]+$/, // Brackets only
  /^[.,;:!?'"]+$/, // Punctuation only
  /^\\n$/, // Newline escape
  /^[a-z]{1,3}$/, // Very short strings (likely keys)
  /^(true|false|null|undefined)$/, // JS literals
  /^(GET|POST|PUT|DELETE|PATCH)$/, // HTTP methods
  /^(sm|md|lg|xl|2xl)$/, // Tailwind breakpoints
  /^(primary|secondary|destructive|outline|ghost|link)$/, // Variant names
  /^(default|small|large|icon)$/, // Size names
]

// Words that indicate technical/non-user text
const TECHNICAL_WORDS = new Set([
  'className',
  'onClick',
  'onChange',
  'onSubmit',
  'ref',
  'key',
  'id',
  'name',
  'type',
  'value',
  'children',
  'style',
  'src',
  'href',
])

// Common i18n key patterns
const I18N_KEY_PATTERNS = [
  /^[a-z]+\.[a-z.]+$/, // dot.notation.keys
  /^[a-z]+:[a-z.]+$/, // namespace:key patterns
]

function isExcludedText(text: string): boolean {
  const trimmed = text.trim()

  // Empty or whitespace
  if (!trimmed || trimmed.length === 0) return true

  // Single character
  if (trimmed.length === 1) return true

  // Check exclusion patterns
  for (const pattern of EXCLUDE_PATTERNS) {
    if (pattern.test(trimmed)) return true
  }

  // Check if it looks like an i18n key
  for (const pattern of I18N_KEY_PATTERNS) {
    if (pattern.test(trimmed)) return true
  }

  // Technical words
  if (TECHNICAL_WORDS.has(trimmed)) return true

  return false
}

function hasEnglishWords(text: string): boolean {
  // Check if text contains English words (at least 2 chars, alphabetic)
  const words = text.match(/[a-zA-Z]{2,}/g)
  if (!words) return false

  // Filter out technical words and check if any remain
  const meaningfulWords = words.filter((word) => {
    const lower = word.toLowerCase()
    // Exclude common technical/code words
    if (
      [
        'div',
        'span',
        'img',
        'svg',
        'px',
        'rem',
        'em',
        'var',
        'const',
        'let',
        'function',
        'return',
        'import',
        'export',
        'from',
        'type',
        'interface',
        'class',
        'extends',
        'implements',
      ].includes(lower)
    ) {
      return false
    }
    return true
  })

  return meaningfulWords.length > 0
}

function getLineAndColumn(
  sourceFile: ts.SourceFile,
  pos: number
): { line: number; column: number } {
  const { line, character } = sourceFile.getLineAndCharacterOfPosition(pos)
  return { line: line + 1, column: character + 1 }
}

function getContext(sourceFile: ts.SourceFile, node: ts.Node): string {
  const start = node.getStart(sourceFile)
  const end = node.getEnd()
  const fullText = sourceFile.getFullText()

  // Get surrounding context (line with some padding)
  const lineStart = fullText.lastIndexOf('\n', start) + 1
  const lineEnd = fullText.indexOf('\n', end)
  const line = fullText.slice(lineStart, lineEnd === -1 ? undefined : lineEnd)

  return line.trim().slice(0, 100)
}

function isInsideTranslationCall(node: ts.Node): boolean {
  let current = node.parent

  while (current) {
    if (ts.isCallExpression(current)) {
      const expr = current.expression
      // Check for t(), i18n.t(), etc.
      if (ts.isIdentifier(expr) && expr.text === 't') {
        return true
      }
      if (ts.isPropertyAccessExpression(expr) && expr.name.text === 't') {
        return true
      }
    }
    // Check for tagged template literals with t
    if (ts.isTaggedTemplateExpression(current)) {
      const tag = current.tag
      if (ts.isIdentifier(tag) && tag.text === 't') {
        return true
      }
    }
    current = current.parent
  }
  return false
}

function isInsideImportOrType(node: ts.Node): boolean {
  let current = node.parent
  while (current) {
    if (
      ts.isImportDeclaration(current) ||
      ts.isExportDeclaration(current) ||
      ts.isTypeAliasDeclaration(current) ||
      ts.isInterfaceDeclaration(current) ||
      ts.isTypeNode(current) ||
      ts.isTypeReferenceNode(current)
    ) {
      return true
    }
    current = current.parent
  }
  return false
}

function isConsoleLog(node: ts.Node): boolean {
  let current = node.parent
  while (current) {
    if (ts.isCallExpression(current)) {
      const expr = current.expression
      if (ts.isPropertyAccessExpression(expr)) {
        const obj = expr.expression
        if (ts.isIdentifier(obj) && obj.text === 'console') {
          return true
        }
      }
    }
    current = current.parent
  }
  return false
}

// Props to completely ignore (not user-visible)
const IGNORE_PROPS = new Set([
  'className',
  'class',
  'style',
  'key',
  'id',
  'name',
  'type',
  'variant',
  'size',
  'asChild',
  'side',
  'align',
  'sideOffset',
  'testId',
  'data-testid',
  'role',
  'tabIndex',
])

function isInsideIgnoredProp(node: ts.Node): boolean {
  let current = node.parent
  while (current) {
    if (ts.isJsxAttribute(current)) {
      const attrName = current.name.getText()
      if (IGNORE_PROPS.has(attrName)) {
        return true
      }
    }
    current = current.parent
  }
  return false
}

function classifySeverity(text: string, type: string, context: string): Severity {
  // High severity: Clearly user-visible UI text
  if (type === 'jsx-text') return 'high'
  if (type === 'string-prop') return 'high'

  // Low severity: Developer-facing messages or technical strings
  const lowSeverityPatterns = [
    /validation|required|invalid|must be|cannot|should/i, // Validation messages
    /^[a-zA-Z]+\.\$\{/, // Template field paths like `equipment.${key}`
    /\$\{.*\}/, // Contains template expressions
    /error|exception|failed/i, // Error messages
    /\.id$|\.ids$/i, // ID field references
  ]

  for (const pattern of lowSeverityPatterns) {
    if (pattern.test(text) || pattern.test(context)) {
      return 'low'
    }
  }

  // Medium: Everything else that looks user-visible
  return 'medium'
}

function isTailwindClasses(text: string): boolean {
  // Common Tailwind patterns
  const tailwindPatterns = [
    /^(flex|grid|block|inline|hidden)/,
    /^(w-|h-|p-|m-|gap-|space-)/,
    /^(text-|bg-|border-|rounded-)/,
    /^(absolute|relative|fixed|sticky)/,
    /^(items-|justify-|self-)/,
    /^(overflow-|z-|opacity-)/,
    /^(hover:|focus:|active:|dark:)/,
    /^(sm:|md:|lg:|xl:|2xl:)/,
    /\s+(flex|grid|w-|h-|p-|m-|text-|bg-|border-|rounded-)/,
  ]

  for (const pattern of tailwindPatterns) {
    if (pattern.test(text)) return true
  }

  // If string is mostly Tailwind utility classes (has multiple dashes and spaces)
  const words = text.split(/\s+/)
  const tailwindWords = words.filter((w) => /^[a-z]+-[a-z0-9/-]+$/.test(w) || /^[a-z]+:[a-z]/.test(w))
  return tailwindWords.length >= words.length * 0.5 && words.length >= 2
}

function isUserVisibleProp(node: ts.Node): string | null {
  if (!node.parent) return null

  // Check if parent is JSX attribute
  if (ts.isJsxAttribute(node.parent)) {
    const attrName = node.parent.name.getText()
    if (USER_VISIBLE_PROPS.has(attrName)) {
      return attrName
    }
  }

  // Check if grandparent is JSX attribute (for expressions like placeholder={""})
  if (node.parent.parent && ts.isJsxAttribute(node.parent.parent)) {
    const attrName = node.parent.parent.name.getText()
    if (USER_VISIBLE_PROPS.has(attrName)) {
      return attrName
    }
  }

  return null
}

function analyzeFile(filePath: string): Detection[] {
  const detections: Detection[] = []
  const content = fs.readFileSync(filePath, 'utf-8')
  const sourceFile = ts.createSourceFile(
    filePath,
    content,
    ts.ScriptTarget.Latest,
    true,
    filePath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS
  )

  const relativePath = path.relative(SRC_DIR, filePath)

  function visit(node: ts.Node) {
    // Skip if inside translation call, import, type definition, or ignored prop
    if (
      isInsideTranslationCall(node) ||
      isInsideImportOrType(node) ||
      isConsoleLog(node) ||
      isInsideIgnoredProp(node)
    ) {
      ts.forEachChild(node, visit)
      return
    }

    // JSX Text (direct text in JSX elements)
    if (ts.isJsxText(node)) {
      const text = node.getText(sourceFile)
      if (!isExcludedText(text) && hasEnglishWords(text)) {
        const { line, column } = getLineAndColumn(sourceFile, node.getStart())
        const ctx = getContext(sourceFile, node)
        detections.push({
          file: relativePath,
          line,
          column,
          type: 'jsx-text',
          text: text.trim().slice(0, 60),
          context: ctx,
          severity: classifySeverity(text, 'jsx-text', ctx),
        })
      }
    }

    // String literals
    if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
      const text = node.text

      // Skip Tailwind classes
      if (isTailwindClasses(text)) {
        ts.forEachChild(node, visit)
        return
      }

      // Check if it's a user-visible prop
      const propName = isUserVisibleProp(node)

      if (!isExcludedText(text) && hasEnglishWords(text)) {
        const { line, column } = getLineAndColumn(sourceFile, node.getStart())

        if (propName) {
          const ctx = `[${propName}] ${getContext(sourceFile, node)}`
          detections.push({
            file: relativePath,
            line,
            column,
            type: 'string-prop',
            text: text.slice(0, 60),
            context: ctx,
            severity: classifySeverity(text, 'string-prop', ctx),
          })
        } else {
          // Check if this looks like user-visible text (not a key/id)
          // Must have spaces or be a complete word/phrase
          if (text.includes(' ') || /^[A-Z][a-z]+/.test(text)) {
            const ctx = getContext(sourceFile, node)
            detections.push({
              file: relativePath,
              line,
              column,
              type: 'string-literal',
              text: text.slice(0, 60),
              context: ctx,
              severity: classifySeverity(text, 'string-literal', ctx),
            })
          }
        }
      }
    }

    // Template literals with embedded expressions
    if (ts.isTemplateExpression(node)) {
      const head = node.head.text
      // Skip if looks like Tailwind classes
      if (isTailwindClasses(head)) {
        ts.forEachChild(node, visit)
        return
      }
      if (!isExcludedText(head) && hasEnglishWords(head)) {
        const { line, column } = getLineAndColumn(sourceFile, node.getStart())
        const ctx = getContext(sourceFile, node)
        detections.push({
          file: relativePath,
          line,
          column,
          type: 'string-literal',
          text: `\`${head}...\``.slice(0, 60),
          context: ctx,
          severity: classifySeverity(head, 'string-literal', ctx),
        })
      }
    }

    ts.forEachChild(node, visit)
  }

  visit(sourceFile)
  return detections
}

function getAllTsFiles(dir: string): string[] {
  const files: string[] = []

  function scan(currentDir: string) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true })

    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name)

      if (entry.isDirectory()) {
        // Skip node_modules, dist, etc.
        if (!['node_modules', 'dist', '.git', 'coverage'].includes(entry.name)) {
          scan(fullPath)
        }
      } else if (entry.isFile()) {
        // Include .ts and .tsx, exclude test files and test utilities
        if (
          (entry.name.endsWith('.ts') || entry.name.endsWith('.tsx')) &&
          !entry.name.includes('.test.') &&
          !entry.name.includes('.spec.') &&
          !fullPath.includes('test-utils') &&
          !fullPath.includes('__tests__') &&
          !fullPath.includes('__mocks__')
        ) {
          files.push(fullPath)
        }
      }
    }
  }

  scan(dir)
  return files
}

function main() {
  console.log('Scanning frontend/src for hardcoded English text...\n')

  const files = getAllTsFiles(SRC_DIR)
  console.log(`Found ${files.length} TypeScript files to analyze\n`)

  const allDetections: Detection[] = []

  for (const file of files) {
    try {
      const detections = analyzeFile(file)
      allDetections.push(...detections)
    } catch (err) {
      console.error(`Error analyzing ${file}:`, err)
    }
  }

  // Group by file
  const byFile = new Map<string, Detection[]>()
  for (const d of allDetections) {
    if (!byFile.has(d.file)) {
      byFile.set(d.file, [])
    }
    byFile.get(d.file)!.push(d)
  }

  // Group by severity
  const bySeverity = {
    high: allDetections.filter((d) => d.severity === 'high'),
    medium: allDetections.filter((d) => d.severity === 'medium'),
    low: allDetections.filter((d) => d.severity === 'low'),
  }

  // Output results
  console.log('='.repeat(80))
  console.log('HARDCODED ENGLISH TEXT DETECTIONS')
  console.log('='.repeat(80))

  // Show severity breakdown
  console.log('\nSEVERITY BREAKDOWN:')
  console.log(`  🔴 HIGH (user-visible UI): ${bySeverity.high.length}`)
  console.log(`  🟡 MEDIUM (potentially visible): ${bySeverity.medium.length}`)
  console.log(`  🟢 LOW (developer-facing): ${bySeverity.low.length}`)

  // Output HIGH severity first (most important)
  if (bySeverity.high.length > 0) {
    console.log('\n' + '='.repeat(80))
    console.log('🔴 HIGH SEVERITY (User-Visible UI Text)')
    console.log('='.repeat(80))

    const highByFile = new Map<string, Detection[]>()
    for (const d of bySeverity.high) {
      if (!highByFile.has(d.file)) highByFile.set(d.file, [])
      highByFile.get(d.file)!.push(d)
    }

    for (const [file, detections] of [...highByFile.entries()].sort((a, b) => b[1].length - a[1].length)) {
      console.log(`\n📄 ${file} (${detections.length})`)
      console.log('-'.repeat(60))
      for (const d of detections) {
        const typeIcon = d.type === 'jsx-text' ? '📝' : d.type === 'string-prop' ? '🏷️' : '💬'
        console.log(`  ${typeIcon} Line ${d.line}: "${d.text}"`)
        console.log(`     ${d.context}`)
      }
    }
  }

  // Output MEDIUM severity
  if (bySeverity.medium.length > 0) {
    console.log('\n' + '='.repeat(80))
    console.log('🟡 MEDIUM SEVERITY (Potentially Visible)')
    console.log('='.repeat(80))

    const medByFile = new Map<string, Detection[]>()
    for (const d of bySeverity.medium) {
      if (!medByFile.has(d.file)) medByFile.set(d.file, [])
      medByFile.get(d.file)!.push(d)
    }

    for (const [file, detections] of [...medByFile.entries()].sort((a, b) => b[1].length - a[1].length)) {
      console.log(`\n📄 ${file} (${detections.length})`)
      for (const d of detections) {
        console.log(`  Line ${d.line}: "${d.text}"`)
      }
    }
  }

  // Output LOW severity (collapsed)
  if (bySeverity.low.length > 0) {
    console.log('\n' + '='.repeat(80))
    console.log('🟢 LOW SEVERITY (Developer-Facing) - Summary Only')
    console.log('='.repeat(80))

    const lowByFile = new Map<string, Detection[]>()
    for (const d of bySeverity.low) {
      if (!lowByFile.has(d.file)) lowByFile.set(d.file, [])
      lowByFile.get(d.file)!.push(d)
    }

    for (const [file, count] of [...lowByFile.entries()].map((e) => [e[0], e[1].length] as const).sort((a, b) => b[1] - a[1])) {
      console.log(`  📄 ${file}: ${count} detections`)
    }
  }

  const totalCount = allDetections.length
  console.log('\n' + '='.repeat(80))
  console.log(`SUMMARY: ${totalCount} hardcoded texts in ${byFile.size} files`)
  console.log(`  Priority: ${bySeverity.high.length} HIGH | ${bySeverity.medium.length} MEDIUM | ${bySeverity.low.length} LOW`)
  console.log('='.repeat(80))

  // Output as JSON for further processing
  const jsonPath = path.join(__dirname, 'hardcoded-text-report.json')
  fs.writeFileSync(
    jsonPath,
    JSON.stringify(
      {
        timestamp: new Date().toISOString(),
        totalDetections: totalCount,
        filesAffected: byFile.size,
        detections: allDetections,
      },
      null,
      2
    )
  )
  console.log(`\nJSON report saved to: ${jsonPath}`)
}

main()
