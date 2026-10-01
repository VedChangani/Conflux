import { createContext } from 'react'
import type { Account, LoginRequest } from './types'

export type AuthStatus = 'anonymous' | 'unverified' | 'authenticated'

export interface AuthContextValue {
  status: AuthStatus
  account: Account | null
  verificationFailed: boolean
  loggedOut: boolean
  login: (credentials: LoginRequest) => Promise<Account>
  logout: () => void
  retryVerification: () => void
  refreshAccount: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
