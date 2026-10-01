import { createContext } from 'react'
import type { Account, LoginRequest } from './types'

/**
 * - `anonymous`: no stored token.
 * - `unverified`: a token is stored but has not been confirmed against `/auth/me`;
 *   it may be expired or revoked, so it must not be treated as signed in.
 * - `authenticated`: the account has been confirmed.
 */
export type AuthStatus = 'anonymous' | 'unverified' | 'authenticated'

export interface AuthContextValue {
  status: AuthStatus
  account: Account | null
  /**
   * True when the stored token could not be checked for a reason other than being
   * rejected (e.g. the server was unreachable). The session stays `unverified`.
   */
  verificationFailed: boolean
  /**
   * True when the session was ended by {@link logout} (until the next login), as opposed
   * to never having existed or having expired. Route guards use it to avoid treating a
   * deliberate logout as an interrupted visit to return to.
   */
  loggedOut: boolean
  /**
   * Logs in, stores the issued token and confirms the account via `/auth/me`.
   * Rejects with the {@link ApiError} on failure; a rejected login leaves the
   * current session untouched.
   */
  login: (credentials: LoginRequest) => Promise<Account>
  /** Removes the token and forgets the account. There is no server-side logout. */
  logout: () => void
  /** Checks the stored token against `/auth/me` again after a failed attempt. */
  retryVerification: () => void
  /**
   * Reads the signed-in account from `/auth/me` again, e.g. after the user changed their
   * display name. Resolves either way; on failure the current account stays.
   */
  refreshAccount: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
