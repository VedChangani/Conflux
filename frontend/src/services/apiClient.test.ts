import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAccessToken, setAccessToken } from '../features/auth/tokenStorage'
import { apiClient, ApiError, onUnauthorized } from './apiClient'

const BASE_URL = 'http://api.test/api/v1'

function mockFetch(response: Response | Error) {
  const fetchMock = vi.fn<typeof fetch>(async () => {
    if (response instanceof Error) {
      throw response
    }
    return response
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

function sentRequest(fetchMock: ReturnType<typeof mockFetch>) {
  const [url, init] = fetchMock.mock.calls[0]
  return { url, init: init!, headers: new Headers(init!.headers) }
}

describe('apiClient', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', `${BASE_URL}/`)
  })

  it('attaches the Bearer token when one is stored', async () => {
    setAccessToken('stored-token')
    const fetchMock = mockFetch(jsonResponse({ ok: true }))

    await apiClient.get('/auth/me')

    expect(sentRequest(fetchMock).headers.get('Authorization')).toBe('Bearer stored-token')
  })

  it('does not send an Authorization header without a token', async () => {
    const fetchMock = mockFetch(jsonResponse({ ok: true }))

    await apiClient.get('/health')

    expect(sentRequest(fetchMock).headers.has('Authorization')).toBe(false)
  })

  it('does not attach the token when auth is disabled for the request', async () => {
    setAccessToken('stored-token')
    const fetchMock = mockFetch(jsonResponse({ ok: true }))

    await apiClient.post('/auth/login', { login: 'alice' }, { auth: false })

    expect(sentRequest(fetchMock).headers.has('Authorization')).toBe(false)
  })

  it('resolves paths against the configured base URL and returns parsed JSON', async () => {
    const fetchMock = mockFetch(jsonResponse({ id: 7 }))

    const result = await apiClient.get<{ id: number }>('/users/alice')

    expect(sentRequest(fetchMock).url).toBe(`${BASE_URL}/users/alice`)
    expect(result).toEqual({ id: 7 })
  })

  it('sends request bodies as JSON', async () => {
    const fetchMock = mockFetch(jsonResponse({}, 201))

    await apiClient.post('/things', { name: 'Conflux' })

    const { init, headers } = sentRequest(fetchMock)
    expect(init.method).toBe('POST')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(init.body).toBe('{"name":"Conflux"}')
  })

  it('resolves empty responses with undefined', async () => {
    mockFetch(new Response(null, { status: 204 }))

    await expect(apiClient.delete('/things/1')).resolves.toBeUndefined()
  })

  it('turns problem+json responses into an ApiError', async () => {
    mockFetch(
      jsonResponse(
        {
          status: 400,
          title: 'Bad Request',
          detail: "Invalid request content.",
          errors: [{ field: 'email', message: 'must be a well-formed email address' }],
        },
        400,
        'application/problem+json',
      ),
    )

    const error = await apiClient.post('/auth/register', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.message).toBe('Invalid request content.')
    expect(apiError.fieldErrors).toEqual([{ field: 'email', message: 'must be a well-formed email address' }])
  })

  it('uses a generic message when the error body is not problem+json', async () => {
    mockFetch(new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }))

    const error = (await apiClient.get('/health').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(502)
    expect(error.problem).toBeNull()
    expect(error.message).toBe('Something went wrong. Please try again.')
  })

  it('clears a rejected token and notifies listeners on 401', async () => {
    setAccessToken('expired-token')
    mockFetch(jsonResponse({ status: 401, detail: 'A valid access token is required.' }, 401, 'application/problem+json'))
    const listener = vi.fn()
    const unsubscribe = onUnauthorized(listener)

    await expect(apiClient.get('/auth/me')).rejects.toMatchObject({ status: 401 })

    expect(getAccessToken()).toBeNull()
    expect(listener).toHaveBeenCalledOnce()
    unsubscribe()
  })

  it('does not notify listeners on 401 for requests sent without a token', async () => {
    mockFetch(jsonResponse({ status: 401, detail: 'Invalid email/username or password.' }, 401, 'application/problem+json'))
    const listener = vi.fn()
    const unsubscribe = onUnauthorized(listener)

    await expect(apiClient.post('/auth/login', {})).rejects.toMatchObject({
      status: 401,
      message: 'Invalid email/username or password.',
    })

    expect(listener).not.toHaveBeenCalled()
    unsubscribe()
  })

  it('reports network failures as an ApiError with status 0', async () => {
    mockFetch(new TypeError('Failed to fetch'))

    await expect(apiClient.get('/health')).rejects.toMatchObject({ name: 'ApiError', status: 0 })
  })

  it('fails clearly when VITE_API_BASE_URL is not configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    const fetchMock = mockFetch(jsonResponse({}))

    await expect(apiClient.get('/health')).rejects.toThrow('VITE_API_BASE_URL is not configured.')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
