import { describe, it, expect, vi } from 'vitest'
import { buildIdPlugin } from './vite-plugin-build-id'
import { VERSION_FILE_PATH } from './src/lib/constants/app'

type ConfigHook = () => { define: Record<string, string> }
type GenerateBundle = (this: unknown) => void

describe('buildIdPlugin', () => {
  it('compiles the build id in as __BUILD_ID__ in every mode', () => {
    const plugin = buildIdPlugin('build-1')
    const config = plugin.config as ConfigHook

    expect(plugin.apply).toBeUndefined()
    expect(config().define).toEqual({ __BUILD_ID__: '"build-1"' })
  })

  it('emits the same build id as version.json', () => {
    const plugin = buildIdPlugin('build-1')
    const emitFile = vi.fn()
    const generateBundle = plugin.generateBundle as GenerateBundle

    generateBundle.call({ emitFile })

    expect(emitFile).toHaveBeenCalledTimes(1)
    const [emitted] = emitFile.mock.calls[0] as [{ type: string; fileName: string; source: string }]
    expect(emitted.type).toBe('asset')
    expect(emitted.fileName).toBe(VERSION_FILE_PATH.slice(1))
    expect(JSON.parse(emitted.source)).toEqual({ buildId: 'build-1' })
  })
})
