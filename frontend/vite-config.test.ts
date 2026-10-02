import { describe, it, expect, vi } from 'vitest'
import path from 'path'
import type { Plugin } from 'vite'
import config from './vite.config'

type GenerateBundle = (this: unknown, outputOptions: { chunkFileNames: unknown }) => void

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
})
