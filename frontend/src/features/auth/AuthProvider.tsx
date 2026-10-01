import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError, onUnauthorized } from '../../services/apiClient'
import { authApi } from './authApi'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'
import { clearAccessToken, hasAccessToken, setAccessToken } from './tokenStorage'
import type { Account, LoginRequest } from './types'

interface AuthState {
  status: AuthStatus
  account: Account | null
  verificationFailed: boolean
  loggedOut: boolean
}

const ANONYMOUS: AuthState = { status: 'anonymous', account: null, verificationFailed: false, loggedOut: false }

const UNVERIFIED: AuthState = { status: 'unverified', account: null, verificationFailed: false, loggedOut: false }

function authenticated(account: Account): AuthState {
  return { status: 'authenticated', account, verificationFailed: false, loggedOut: false }
}

async function checkStoredToken(): Promise<AuthState> {
  try {
    return authenticated(await authApi.me())
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return ANONYMOUS
    }
    return { ...UNVERIFIED, verificationFailed: true }
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => (hasAccessToken() ? UNVERIFIED : ANONYMOUS))
  const sessionVersion = useRef(0)
  const verifying = useRef(false)

  const verifyStoredToken = useCallback(() => {
    if (verifying.current) {
      return
    }
    verifying.current = true
    const version = sessionVersion.current
    void checkStoredToken().then((next) => {
      verifying.current = false
      if (version === sessionVersion.current) {
        setState(next)
      }
    })
  }, [])

  useEffect(() => {
    if (hasAccessToken()) {
      verifyStoredToken()
    }
  }, [verifyStoredToken])

  useEffect(
    () =>
      onUnauthorized(() => {
        sessionVersion.current += 1
        setState(ANONYMOUS)
      }),
    [],
  )

  const retryVerification = useCallback(() => {
    setState(UNVERIFIED)
    verifyStoredToken()
  }, [verifyStoredToken])

  const login = useCallback(async (credentials: LoginRequest) => {
    const { accessToken } = await authApi.login(credentials)
    const version = ++sessionVersion.current
    setAccessToken(accessToken)
    try {
      const account = await authApi.me()
      if (version === sessionVersion.current) {
        setState(authenticated(account))
      }
      return account
    } catch (error) {
      if (version === sessionVersion.current) {
        clearAccessToken()
        setState(ANONYMOUS)
      }
      throw error
    }
  }, [])

  const refreshAccount = useCallback(async () => {
    const version = sessionVersion.current
    try {
      const account = await authApi.me()
      if (version === sessionVersion.current) {
        setState((current) => (current.status === 'authenticated' ? authenticated(account) : current))
      }
    } catch {
    }
  }, [])

  const logout = useCallback(() => {
    sessionVersion.current += 1
    clearAccessToken()
    setState({ ...ANONYMOUS, loggedOut: true })
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, login, logout, retryVerification, refreshAccount }),
    [state, login, logout, retryVerification, refreshAccount],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
