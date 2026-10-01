import { describe, expect, it } from 'vitest'
import { ApiError } from '../services/apiClient'
import { loadErrorMessage } from './errors'
import { errorMessageOf } from './formErrors'

const LEAKY_DETAIL = 'org.hibernate.exception.JDBCConnectionException: unable to acquire connection'

function apiError(status: number, detail = LEAKY_DETAIL, errors?: { field: string; message: string }[]) {
  return new ApiError(status, { status, detail, ...(errors && { errors }) })
}

describe('loadErrorMessage', () => {
  it('passes through only the client’s own network message', () => {
    const offline = new ApiError(0, null, 'Unable to reach the server. Check your connection and try again.')

    expect(loadErrorMessage(offline)).toBe('Unable to reach the server. Check your connection and try again.')
  })

  it.each([
    [403, 'Your account doesn’t have access to this right now. It may be suspended.'],
    [429, 'Too many requests in a short time. Wait a moment, then try again.'],
    [404, 'Something went wrong. Please try again.'],
    [409, 'Something went wrong. Please try again.'],
    [500, 'Something went wrong. Please try again.'],
    [503, 'Something went wrong. Please try again.'],
  ])('explains a %i in the app’s own words', (status, message) => {
    expect(loadErrorMessage(apiError(status))).toBe(message)
  })

  it('falls back to the generic message for anything that isn’t an API error', () => {
    expect(loadErrorMessage(new Error(LEAKY_DETAIL))).toBe('Something went wrong. Please try again.')
  })
})

describe('errorMessageOf (login and registration)', () => {
  it('points at the highlighted fields for field validation errors', () => {
    expect(errorMessageOf(apiError(400, 'Invalid request content.', [{ field: 'email', message: 'x' }]))).toBe(
      'Please correct the highlighted fields.',
    )
  })

  it('keeps the auth endpoints’ own wrong-credentials and already-taken explanations', () => {
    expect(errorMessageOf(apiError(401, 'Invalid email/username or password.'))).toBe(
      'Invalid email/username or password.',
    )
    expect(errorMessageOf(apiError(409, 'Email is already registered.'))).toBe('Email is already registered.')
  })

  it.each([
    [400, 'Some details weren’t accepted. Check the form and try again.'],
    [429, 'Too many attempts in a short time. Wait a few minutes, then try again.'],
    [500, 'Something went wrong. Please try again.'],
    [503, 'Something went wrong. Please try again.'],
  ])('never shows backend wording for a %i', (status, message) => {
    expect(errorMessageOf(apiError(status))).toBe(message)
  })
})
