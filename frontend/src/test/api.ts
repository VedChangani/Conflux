import { vi } from 'vitest'
import type { Account } from '../features/auth/types'
import type { FieldError } from '../types/api'

export const TEST_API_BASE_URL = 'http://api.test/api/v1'

export const ACCOUNT: Account = {
  id: 42,
  email: 'ada@example.com',
  username: 'ada',
  displayName: 'Ada Lovelace',
  role: 'USER',
  status: 'ACTIVE',
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** A backend-style RFC 9457 error response. */
export function problem(status: number, detail: string, errors?: FieldError[]): Response {
  return new Response(JSON.stringify({ type: 'about:blank', status, detail, ...(errors && { errors }) }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

export interface RecordedRequest {
  method: string
  path: string
  headers: Headers
  body: unknown
}

type Handler = () => Response | Promise<Response>

/**
 * Stubs `fetch` with handlers keyed by `"METHOD /path"` (relative to the API base URL)
 * and records every request. The handler map may be changed during a test.
 */
export function mockApi(handlers: Record<string, Handler>) {
  vi.stubEnv('VITE_API_BASE_URL', TEST_API_BASE_URL)
  const requests: RecordedRequest[] = []
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const method = init?.method ?? 'GET'
    const path = String(input).slice(TEST_API_BASE_URL.length)
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined
    requests.push({ method, path, headers: new Headers(init?.headers), body })
    const handler = handlers[`${method} ${path}`]
    return handler ? handler() : problem(404, `No mock for ${method} ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { handlers, requests }
}

/** A promise whose settlement the test controls, for observing in-flight states. */
export function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
