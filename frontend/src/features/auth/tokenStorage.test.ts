import { describe, expect, it } from 'vitest'
import { clearAccessToken, getAccessToken, hasAccessToken, setAccessToken } from './tokenStorage'

describe('tokenStorage', () => {
  it('has no token initially', () => {
    expect(getAccessToken()).toBeNull()
    expect(hasAccessToken()).toBe(false)
  })

  it('stores and retrieves the token', () => {
    setAccessToken('header.payload.signature')

    expect(getAccessToken()).toBe('header.payload.signature')
    expect(hasAccessToken()).toBe(true)
  })

  it('persists the token across page loads', () => {
    setAccessToken('persisted-token')

    expect(window.localStorage.getItem('conflux.accessToken')).toBe('persisted-token')
  })

  it('removes the token', () => {
    setAccessToken('header.payload.signature')

    clearAccessToken()

    expect(getAccessToken()).toBeNull()
    expect(hasAccessToken()).toBe(false)
  })
})
