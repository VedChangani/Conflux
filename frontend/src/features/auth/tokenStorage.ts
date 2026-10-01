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
  }
}

export function hasAccessToken(): boolean {
  return getAccessToken() !== null
}
