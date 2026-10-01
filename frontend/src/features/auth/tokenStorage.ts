/**
 * Persists the JWT access token sent as `Authorization: Bearer <token>`.
 *
 * Uses localStorage so a session survives reloads. If storage turns out to be
 * unusable (private mode, blocked site data), the token is kept in memory for this
 * page only. Token values are never logged.
 */
const STORAGE_KEY = 'conflux.accessToken'

let memoryOnly = false
let memoryToken: string | null = null

export function getAccessToken(): string | null {
  if (!memoryOnly) {
    try {
      return window.localStorage.getItem(STORAGE_KEY)
    } catch {
      memoryOnly = true
    }
  }
  return memoryToken
}

export function setAccessToken(token: string): void {
  if (!memoryOnly) {
    try {
      window.localStorage.setItem(STORAGE_KEY, token)
      return
    } catch {
      memoryOnly = true
    }
  }
  memoryToken = token
}

export function clearAccessToken(): void {
  memoryToken = null
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage unusable: nothing was persisted.
  }
}

export function hasAccessToken(): boolean {
  return getAccessToken() !== null
}
