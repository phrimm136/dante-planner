import { http, HttpResponse } from 'msw'

export const handlers = [
  // Warning: use absolute URLs — Node's fetch (test runtime) rejects relative
  // paths, so a relative pattern here would never match and silently never fire.
  http.get('http://localhost:8080/api/health', () => HttpResponse.json({ status: 'ok' })),
]
