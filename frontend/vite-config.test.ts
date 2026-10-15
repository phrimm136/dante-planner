import { describe, it, expect, vi } from 'vitest'
import fs from 'fs'
import path from 'path'
import type { Plugin } from 'vite'
import config from './vite.config'
import { VERSION_FILE_PATH } from './src/lib/constants/app'

type GenerateBundle = (this: unknown, outputOptions: { chunkFileNames: unknown }) => void
type ConfigHook = () => { define: Record<string, string> }

function isPlugin(value: unknown): value is Plugin {
  return typeof value === 'object' && value !== null && 'name' in value
}

function findPlugin(name: string): Plugin | undefined {
  const plugins: unknown[] = config.plugins ?? []
  return plugins
    .flat(Infinity)
    .filter(isPlugin)
    .find((plugin) => plugin.name === name)
}

function outputOptions() {
  const output = config.build?.rolldownOptions?.output
  if (output === undefined || Array.isArray(output)) {
    throw new Error('expected a single build.rolldownOptions.output object')
  }
  return output
}

function directoryOf(fileNames: unknown): string {
  if (typeof fileNames !== 'string') throw new Error(`expected a string, got ${typeof fileNames}`)
  return path.posix.dirname(fileNames)
}

describe('vite.config.ts', () => {
  it('registers the asset-not-found plugin', () => {
    expect(findPlugin('asset-not-found')).toBeDefined()
  })

  it('writes 404.html into the directory every entry, chunk and asset is named into', () => {
    const plugin = findPlugin('asset-not-found')
    if (plugin === undefined) throw new Error('asset-not-found is not registered')
    const output = outputOptions()
    const emitFile = vi.fn()
    const generateBundle = plugin.generateBundle as GenerateBundle

    generateBundle.call(
      {
        emitFile,
        error: (message: string) => {
          throw new Error(message)
        },
      },
      { chunkFileNames: output.chunkFileNames },
    )

    const [emitted] = emitFile.mock.calls[0] as [{ fileName: string }]
    const notFoundDir = path.posix.dirname(emitted.fileName)
    expect({
      entryFileNames: directoryOf(output.entryFileNames),
      chunkFileNames: directoryOf(output.chunkFileNames),
      assetFileNames: directoryOf(output.assetFileNames),
    }).toEqual({
      entryFileNames: notFoundDir,
      chunkFileNames: notFoundDir,
      assetFileNames: notFoundDir,
    })
  })

  it('registers the build-id plugin, emitting the file the client polls', () => {
    const plugin = findPlugin('build-id')
    if (plugin === undefined) throw new Error('build-id is not registered')
    const emitFile = vi.fn()
    const generateBundle = plugin.generateBundle as GenerateBundle
    const configHook = plugin.config as ConfigHook

    generateBundle.call({ emitFile }, { chunkFileNames: undefined })

    const [emitted] = emitFile.mock.calls[0] as [{ fileName: string; source: string }]
    expect(`/${emitted.fileName}`).toBe(VERSION_FILE_PATH)
    expect(JSON.stringify(JSON.parse(emitted.source).buildId)).toBe(
      configHook().define.__BUILD_ID__,
    )
  })

  it('never inlines a font, since font-src in static/_headers forbids data: URIs', () => {
    const limit = config.build?.assetsInlineLimit
    if (typeof limit !== 'function') throw new Error('expected assetsInlineLimit to be a function')
    const fontPath = path.resolve(__dirname, 'src/assets/fonts/planner-numerals.ttf')
    const font = fs.readFileSync(fontPath)

    expect(limit(fontPath, font)).toBe(false)
    expect(limit('/icon.png', Buffer.alloc(16))).toBeUndefined()
  })
})
