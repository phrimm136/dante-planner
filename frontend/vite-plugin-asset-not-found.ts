import path from 'path'
import type { Plugin } from 'vite'

const ASSET_NOT_FOUND_HTML =
  '<!doctype html><meta charset="utf-8"><title>404</title><p>Not found</p>\n'

export function assetNotFoundPlugin(): Plugin {
  return {
    name: 'asset-not-found',
    apply: 'build',
    generateBundle(outputOptions) {
      const { chunkFileNames } = outputOptions
      const dir = typeof chunkFileNames === 'string' ? path.posix.dirname(chunkFileNames) : '.'
      if (dir === '.') {
        this.error(
          'asset-not-found: output.chunkFileNames must be a string with a directory to place 404.html beside the chunks',
        )
      }
      this.emitFile({ type: 'asset', fileName: `${dir}/404.html`, source: ASSET_NOT_FOUND_HTML })
    },
  }
}
