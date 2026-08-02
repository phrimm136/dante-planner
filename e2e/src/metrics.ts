import { readFileSync } from 'node:fs'

// Either a scraped file the runner captured over SSM, or a URL that answers Prometheus text.
const METRICS_SOURCE = process.env.E2E_METRICS_SOURCE

const GTID_CAPTURE_METRIC = 'gtid_capture'

export interface MetricSample {
  name: string
  labels: Record<string, string>
  value: number
}

export function metricsSourceConfigured(): boolean {
  return Boolean(METRICS_SOURCE)
}

async function readSource(): Promise<string> {
  if (!METRICS_SOURCE) throw new Error('E2E_METRICS_SOURCE is unset')

  if (/^https?:\/\//.test(METRICS_SOURCE)) {
    const response = await fetch(METRICS_SOURCE)
    if (!response.ok) {
      throw new Error(`${METRICS_SOURCE} answered ${response.status}`)
    }
    return response.text()
  }
  return readFileSync(METRICS_SOURCE, 'utf8')
}

// Micrometer's Prometheus rendering leaves a trailing comma inside the label braces, so the label
// list is scanned pair by pair rather than split on a strict grammar.
const SAMPLE_LINE = /^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+(-?[\d.eE+-]+|NaN)$/
const LABEL_PAIR = /([a-zA-Z_][a-zA-Z0-9_]*)="((?:[^"\\]|\\.)*)"/g

export function parsePrometheusText(text: string): MetricSample[] {
  const samples: MetricSample[] = []

  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (trimmed.length === 0 || trimmed.startsWith('#')) continue

    const match = trimmed.match(SAMPLE_LINE)
    if (!match) continue

    const labels: Record<string, string> = {}
    for (const pair of (match[2] ?? '').matchAll(LABEL_PAIR)) {
      labels[pair[1]!] = pair[2]!.replace(/\\"/g, '"')
    }
    samples.push({ name: match[1]!, labels, value: Number(match[3]) })
  }

  return samples
}

/**
 * Sums a counter across every series that carries the wanted labels, so a fleet-wide scrape of
 * several pods adds up rather than picking one arbitrarily.
 */
export function counterTotal(
  samples: MetricSample[],
  metric: string,
  labels: Record<string, string>,
): number {
  return samples
    .filter((sample) => sample.name === metric || sample.name === `${metric}_total`)
    .filter((sample) =>
      Object.entries(labels).every(([name, value]) => sample.labels[name] === value),
    )
    .reduce((total, sample) => total + sample.value, 0)
}

export async function scrapeMetrics(): Promise<MetricSample[]> {
  return parsePrometheusText(await readSource())
}

/**
 * The share of GTID captures that fell back to the primary's whole executed set. A ratio near 1
 * means `session_track_gtids` or `trackSessionState` is not in effect and every gated read pays a
 * cross-region round trip (docs/multi-region-request-paths.md §4b).
 *
 * @returns the ratio, or null when the scrape carries no capture samples at all
 */
export async function gtidFallbackRatio(): Promise<number | null> {
  const samples = await scrapeMetrics()
  const tracker = counterTotal(samples, GTID_CAPTURE_METRIC, { source: 'tracker' })
  const fallback = counterTotal(samples, GTID_CAPTURE_METRIC, { source: 'fallback' })
  const total = tracker + fallback

  return total === 0 ? null : fallback / total
}
