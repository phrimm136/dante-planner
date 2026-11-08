import { chromium, type Browser } from '@playwright/test'

export interface BrowserProcess {
  type: string
  id: number
}

export function launchSingleRendererBrowser(): Promise<Browser> {
  return chromium.launch({
    args: ['--renderer-process-limit=1', '--disable-features=SpareRendererForSitePerProcess'],
  })
}

export function onlyRendererPid(processes: readonly BrowserProcess[]): number {
  const renderers = processes.filter((entry) => entry.type === 'renderer')
  if (renderers.length !== 1) {
    throw new Error(`expected exactly one renderer process, found ${renderers.length}`)
  }
  return renderers[0].id
}

export async function armOnlyRendererKill(browser: Browser): Promise<() => void> {
  const session = await browser.newBrowserCDPSession()
  const { processInfo } = await session.send('SystemInfo.getProcessInfo')
  await session.detach()
  const pid = onlyRendererPid(processInfo)
  return () => {
    process.kill(pid, 'SIGKILL')
  }
}
