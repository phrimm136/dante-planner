import type { Plugin } from 'vite'
import { VERSION_FILE_PATH } from './src/lib/constants/app'

export function buildIdPlugin(buildId: string): Plugin {
  return {
    name: 'build-id',
    config: () => ({ define: { __BUILD_ID__: JSON.stringify(buildId) } }),
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: VERSION_FILE_PATH.slice(1),
        source: JSON.stringify({ buildId }),
      })
    },
  }
}
