import { describe, it, expect, vi } from 'vitest'
import { assetNotFoundPlugin } from './vite-plugin-asset-not-found'

type GenerateBundle = (this: unknown, outputOptions: { chunkFileNames: unknown }) => void

function runGenerateBundle(chunkFileNames: unknown) {
  const plugin = assetNotFoundPlugin()
  const emitFile = vi.fn()
  const error = vi.fn((message: string) => {
    throw new Error(message)
  })
  const generateBundle = plugin.generateBundle as GenerateBundle
  const run = () => generateBundle.call({ emitFile, error }, { chunkFileNames })
  return { plugin, emitFile, error, run }
}

describe('assetNotFoundPlugin', () => {
  it.each([
    { chunkFileNames: 'a/[hash:12].js', expected: 'a/404.html' },
    { chunkFileNames: 'x/y/[hash].js', expected: 'x/y/404.html' },
  ])('emits $expected for chunkFileNames $chunkFileNames', ({ chunkFileNames, expected }) => {
    const { plugin, emitFile, error, run } = runGenerateBundle(chunkFileNames)

    run()

    expect(plugin.apply).toBe('build')
    expect(error).not.toHaveBeenCalled()
    expect(emitFile).toHaveBeenCalledTimes(1)
    const [emitted] = emitFile.mock.calls[0] as [{ type: string; fileName: string; source: string }]
    expect(emitted.type).toBe('asset')
    expect(emitted.fileName).toBe(expected)
    expect(emitted.source).toContain('<!doctype html>')
  })

  it.each([
    { label: 'a function', chunkFileNames: () => 'a/[hash].js' },
    { label: 'a name without a directory', chunkFileNames: '[hash].js' },
  ])('fails the build when chunkFileNames is $label', ({ chunkFileNames }) => {
    const { emitFile, error, run } = runGenerateBundle(chunkFileNames)

    expect(run).toThrow(/chunkFileNames/)
    expect(error).toHaveBeenCalledTimes(1)
    expect(emitFile).not.toHaveBeenCalled()
  })
})
