const ENTITY_FILE = /\/pages\/[^/]+\/lib\/[^/]+Entity\.ts$/
const ENTITY_EXPORT = /^to(Unknown)?[A-Z]\w*Entity$/
const UNKNOWN_EXPORT = /^toUnknown[A-Z]\w*Entity$/
const PLURAL_BUILDER = /^to[A-Z]\w*Entities$/
const BUILDER_FACTORY = 'createEntityBuilder'

const singularMessage = (name) =>
  `Entity module exports only to<Noun>Entity builders made by ${BUILDER_FACTORY} (or a toUnknown<Noun>Entity placeholder); found \`${name}\`.`

const pluralMessage = (name) =>
  `\`${name}\` is a plural builder; build one entity with to<Noun>Entity and map at the call site.`

const isTypeOnly = (node) => node.exportKind === 'type' || node.importKind === 'type'

const isFunctionLike = (node) =>
  node !== null &&
  node !== undefined &&
  (node.type === 'ArrowFunctionExpression' || node.type === 'FunctionExpression')

const isBuilderCall = (init) =>
  init !== null &&
  init !== undefined &&
  init.type === 'CallExpression' &&
  init.callee.type === 'Identifier' &&
  init.callee.name === BUILDER_FACTORY

const singularBuilder = {
  meta: {
    type: 'problem',
    schema: [],
  },
  create(context) {
    const filename = context.filename ?? context.getFilename()
    if (!ENTITY_FILE.test(filename.replaceAll('\\', '/'))) return {}

    const report = (node, name) => {
      context.report({ node, message: singularMessage(name) })
    }

    const checkNamed = (idNode, name, shape) => {
      if (!ENTITY_EXPORT.test(name)) {
        report(idNode, name)
        return
      }
      if (UNKNOWN_EXPORT.test(name)) {
        const fn = shape.fn
        if (!isFunctionLike(fn) && shape.kind !== 'function') {
          report(idNode, name)
          return
        }
        const params = shape.kind === 'function' ? shape.params : fn.params
        if (params.length < 1 || params.length > 2) report(idNode, name)
        return
      }
      if (shape.kind !== 'const' || !isBuilderCall(shape.init)) report(idNode, name)
    }

    return {
      ExportNamedDeclaration(node) {
        if (isTypeOnly(node)) return

        const declaration = node.declaration
        if (declaration) {
          if (
            declaration.type === 'TSTypeAliasDeclaration' ||
            declaration.type === 'TSInterfaceDeclaration' ||
            declaration.type === 'TSEnumDeclaration' ||
            declaration.type === 'TSModuleDeclaration'
          ) {
            return
          }
          if (declaration.type === 'VariableDeclaration') {
            if (declaration.declare) return
            for (const declarator of declaration.declarations) {
              if (declarator.id.type !== 'Identifier') {
                report(declarator.id, context.sourceCode.getText(declarator.id))
                continue
              }
              checkNamed(declarator.id, declarator.id.name, {
                kind: declaration.kind,
                init: declarator.init,
                fn: declarator.init,
              })
            }
            return
          }
          if (
            declaration.type === 'FunctionDeclaration' ||
            declaration.type === 'TSDeclareFunction'
          ) {
            if (declaration.id) {
              checkNamed(declaration.id, declaration.id.name, {
                kind: 'function',
                params: declaration.params,
              })
            }
            return
          }
          if (declaration.id) report(declaration.id, declaration.id.name)
          return
        }

        for (const specifier of node.specifiers) {
          if (isTypeOnly(specifier)) continue
          const exported = specifier.exported
          const name = exported.type === 'Identifier' ? exported.name : exported.value
          if (!ENTITY_EXPORT.test(name)) report(exported, name)
        }
      },

      ExportDefaultDeclaration(node) {
        if (isTypeOnly(node)) return
        context.report({ node, message: singularMessage('default') })
      },
    }
  },
}

const noPluralBuilder = {
  meta: {
    type: 'problem',
    schema: [],
  },
  create(context) {
    const seen = new Set()

    const check = (node) => {
      if (!node || node.type !== 'Identifier') return
      if (!PLURAL_BUILDER.test(node.name)) return
      const key = `${node.start ?? node.range?.[0]}:${node.end ?? node.range?.[1]}`
      if (seen.has(key)) return
      seen.add(key)
      context.report({ node, message: pluralMessage(node.name) })
    }

    return {
      FunctionDeclaration(node) {
        check(node.id)
      },
      TSDeclareFunction(node) {
        check(node.id)
      },
      VariableDeclarator(node) {
        check(node.id)
      },
      ImportSpecifier(node) {
        check(node.local)
        check(node.imported)
      },
      ImportDefaultSpecifier(node) {
        check(node.local)
      },
      ImportNamespaceSpecifier(node) {
        check(node.local)
      },
      ExportSpecifier(node) {
        check(node.local)
        check(node.exported)
      },
    }
  },
}

export default {
  meta: { name: 'entity' },
  rules: {
    'singular-builder': singularBuilder,
    'no-plural-builder': noPluralBuilder,
  },
}
