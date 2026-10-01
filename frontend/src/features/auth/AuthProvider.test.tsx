import { act, renderHook, waitFor } from '@testing-library/react'
import { StrictMode, type ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { apiClient, ApiError } from '../../services/apiClient'
import { ACCOUNT, deferred, json, mockApi, problem } from '../../test/api'
import { AuthProvider } from './AuthProvider'
import { getAccessToken, setAccessToken } from './tokenStorage'
import { useAuth } from './useAuth'

function renderAuth() {
  return renderHook(() => useAuth(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <StrictMode>
        <AuthProvider>{children}</AuthProvider>
      </StrictMode>
    ),
  })
}

const meRequests = (requests: { method: string; path: string }[]) =>
  requests.filter((request) => request.method === 'GET' && request.path === '/auth/me')

describe('AuthProvider startup', () => {
  it('stays anonymous without calling the API when no token is stored', () => {
    const { requests } = mockApi({})

    const { result } = renderAuth()

    expect(result.current.status).toBe('anonymous')
    expect(result.current.account).toBeNull()
    expect(requests).toHaveLength(0)
  })

  it('is unverified while /auth/me is pending, then authenticated for a valid token', async () => {
    setAccessToken('stored-token')
    const me = deferred<Response>()
    const { requests } = mockApi({ 'GET /auth/me': () => me.promise })

    const { result } = renderAuth()

    expect(result.current.status).toBe('unverified')
    expect(result.current.account).toBeNull()

    me.resolve(json(ACCOUNT))

    await waitFor(() => expect(result.current.status).toBe('authenticated'))
    expect(result.current.account).toEqual(ACCOUNT)
    expect(meRequests(requests)).toHaveLength(1)
    expect(requests[0].headers.get('Authorization')).toBe('Bearer stored-token')
  })

  it('does not repeat /auth/me on re-renders', async () => {
    setAccessToken('stored-token')
    const { requests } = mockApi({ 'GET /auth/me': () => json(ACCOUNT) })

    const { result, rerender } = renderAuth()
    await waitFor(() => expect(result.current.status).toBe('authenticated'))
    rerender()
    rerender()

    expect(meRequests(requests)).toHaveLength(1)
  })

  it('clears an invalid or expired token and becomes anonymous', async () => {
    setAccessToken('expired-token')
    mockApi({ 'GET /auth/me': () => problem(401, 'A valid access token is required.') })

    const { result } = renderAuth()

    await waitFor(() => expect(result.current.status).toBe('anonymous'))
    expect(result.current.account).toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('keeps the token and allows a retry when the server cannot be reached', async () => {
    setAccessToken('stored-token')
    const { handlers } = mockApi({
      'GET /auth/me': () => {
        throw new TypeError('Failed to fetch')
      },
    })

    const { result } = renderAuth()

    await waitFor(() => expect(result.current.verificationFailed).toBe(true))
    expect(result.current.status).toBe('unverified')
    expect(getAccessToken()).toBe('stored-token')

    handlers['GET /auth/me'] = () => json(ACCOUNT)
    act(() => result.current.retryVerification())

    await waitFor(() => expect(result.current.status).toBe('authenticated'))
    expect(result.current.verificationFailed).toBe(false)
  })
})

describe('AuthProvider login', () => {
  it('logs in, stores the token and confirms the account', async () => {
    const { requests } = mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
    })
    const { result } = renderAuth()

    let account: unknown
    await act(async () => {
      account = await result.current.login({ identifier: 'ada', password: 'correct horse' })
    })

    expect(account).toEqual(ACCOUNT)
    expect(result.current.status).toBe('authenticated')
    expect(result.current.account).toEqual(ACCOUNT)
    expect(getAccessToken()).toBe('new-token')

    const [loginRequest, meRequest] = requests
    expect(loginRequest.path).toBe('/auth/login')
    expect(loginRequest.body).toEqual({ identifier: 'ada', password: 'correct horse' })
    expect(loginRequest.headers.has('Authorization')).toBe(false)
    expect(meRequest.path).toBe('/auth/me')
    expect(meRequest.headers.get('Authorization')).toBe('Bearer new-token')
  })

  it('rejects with the backend error and stays anonymous when credentials are wrong', async () => {
    const { requests } = mockApi({
      'POST /auth/login': () => problem(401, 'Invalid email/username or password.'),
    })
    const { result } = renderAuth()

    let error: unknown
    await act(async () => {
      error = await result.current.login({ identifier: 'ada', password: 'wrong' }).catch((caught: unknown) => caught)
    })

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('Invalid email/username or password.')
    expect(result.current.status).toBe('anonymous')
    expect(getAccessToken()).toBeNull()
    expect(meRequests(requests)).toHaveLength(0)
  })

  it('does not disturb an existing session when a login attempt fails', async () => {
    setAccessToken('stored-token')
    mockApi({
      'GET /auth/me': () => json(ACCOUNT),
      'POST /auth/login': () => problem(401, 'Invalid email/username or password.'),
    })
    const { result } = renderAuth()
    await waitFor(() => expect(result.current.status).toBe('authenticated'))

    await act(async () => {
      await result.current.login({ identifier: 'someone', password: 'wrong' }).catch(() => undefined)
    })

    expect(result.current.status).toBe('authenticated')
    expect(result.current.account).toEqual(ACCOUNT)
    expect(getAccessToken()).toBe('stored-token')
  })

  it('discards the new token if the account cannot be confirmed', async () => {
    mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => problem(500, 'An unexpected error occurred.'),
    })
    const { result } = renderAuth()

    await act(async () => {
      await result.current.login({ identifier: 'ada', password: 'correct horse' }).catch(() => undefined)
    })

    expect(result.current.status).toBe('anonymous')
    expect(getAccessToken()).toBeNull()
  })
})

describe('AuthProvider logout', () => {
  it('clears the token and the account', async () => {
    setAccessToken('stored-token')
    mockApi({ 'GET /auth/me': () => json(ACCOUNT) })
    const { result } = renderAuth()
    await waitFor(() => expect(result.current.status).toBe('authenticated'))

    act(() => result.current.logout())

    expect(result.current.status).toBe('anonymous')
    expect(result.current.account).toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('ends the session when any request reports the token as rejected', async () => {
    setAccessToken('stored-token')
    const { handlers } = mockApi({ 'GET /auth/me': () => json(ACCOUNT) })
    const { result } = renderAuth()
    await waitFor(() => expect(result.current.status).toBe('authenticated'))

    handlers['GET /auth/me'] = () => problem(401, 'A valid access token is required.')
    await act(async () => {
      await apiClient.get('/auth/me').catch(() => undefined)
    })

    expect(result.current.status).toBe('anonymous')
    expect(getAccessToken()).toBeNull()
  })
})
