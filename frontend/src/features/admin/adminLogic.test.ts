import { describe, expect, it } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { isActiveAdmin } from './access'
import { adminActionError, adminLoadError, noteLengthError, serverNoteError } from './adminErrors'
import { readReportsQuery, toReportsApiSearch, withReportsQuery } from './reportQuery'
import type { AdminAction } from './types'

const LEAK = 'Raw backend detail'
const apiError = (status: number, errors?: { field: string; message: string }[]) =>
  new ApiError(status, { status, detail: LEAK, errors })

describe('admin access', () => {
  const base = { id: 1, email: 'a@example.com', username: 'a', displayName: 'A' }

  it('is only for active administrators', () => {
    expect(isActiveAdmin({ ...base, role: 'ADMIN', status: 'ACTIVE' })).toBe(true)
    expect(isActiveAdmin({ ...base, role: 'ADMIN', status: 'SUSPENDED' })).toBe(false)
    expect(isActiveAdmin({ ...base, role: 'USER', status: 'ACTIVE' })).toBe(false)
    expect(isActiveAdmin(null)).toBe(false)
  })
})

describe('report queue query', () => {
  it('defaults to OPEN, the first page and 20 per page, as the backend does', () => {
    const query = readReportsQuery(new URLSearchParams())

    expect(query).toEqual({ status: 'OPEN', page: 1, size: 20 })
    expect(toReportsApiSearch(query)).toBe('status=OPEN&page=0&size=20')
  })

  it('sends the zero-based page and passes the status through for the backend to validate', () => {
    expect(toReportsApiSearch(readReportsQuery(new URLSearchParams('status=RESOLVED&page=3&size=50')))).toBe(
      'status=RESOLVED&page=2&size=50',
    )
    expect(readReportsQuery(new URLSearchParams('status=bogus')).status).toBe('bogus')
  })

  it.each(['0', '51', '-1', '2.5', 'abc', '9999'])('ignores a page size the backend rejects (%s)', (size) => {
    expect(readReportsQuery(new URLSearchParams(`size=${size}`)).size).toBe(20)
  })

  it('keeps defaults out of the URL and returns to the first page on any change', () => {
    const params = new URLSearchParams('status=DISMISSED&page=4&size=10')

    expect(withReportsQuery(params, { status: 'OPEN' })).toBe('size=10')
    expect(withReportsQuery(params, { size: 20 })).toBe('status=DISMISSED')
    expect(withReportsQuery(params, { page: 2 })).toBe('status=DISMISSED&page=2&size=10')
    expect(withReportsQuery(params, { status: 'RESOLVED' })).toBe('status=RESOLVED&size=10')
  })
})

describe('admin load errors', () => {
  it.each([
    [403, 'forbidden', 'Administrator access required', false],
    [404, 'notFound', 'Report not found', false],
    [429, 'rateLimited', 'We couldn’t load this report', true],
    [500, 'server', 'We couldn’t load this report', true],
    [0, 'network', 'We couldn’t load this report', true],
  ] as const)('maps %i without backend wording', (status, kind, title, retryable) => {
    const result = adminLoadError(apiError(status), 'report')

    expect(result).toMatchObject({ kind, title, retryable })
    expect(result.message).not.toContain(LEAK)
  })

  it('treats a 400 from the queue as invalid filters', () => {
    expect(adminLoadError(apiError(400), 'queue')).toMatchObject({ kind: 'invalid', title: 'These filters aren’t valid' })
  })
})

describe('admin action errors', () => {
  it.each<[AdminAction, number, string, boolean, boolean]>([
    ['resolve', 409, 'This report has already been reviewed, so it can’t be changed. Its current status is shown.', false, true],
    ['dismiss', 404, 'This report no longer exists.', false, true],
    ['suspendUser', 409, 'You can’t suspend your own account.', false, false],
    ['restoreUser', 404, 'This account no longer exists.', false, true],
    [
      'suspendListing',
      409,
      'Only published listings can be suspended, and only suspended listings restored. The listing’s current status is shown.',
      false,
      true,
    ],
    ['restoreListing', 429, 'You’ve made a lot of moderation changes in a short time. Wait a moment, then try again.', true, false],
    ['resolve', 500, 'Something went wrong, so this may not have been saved. Please try again.', true, false],
    ['resolve', 0, 'Unable to reach the server. Nothing may have changed; check your connection and try again.', true, false],
  ])('%s %i', (action, status, message, retryable, refresh) => {
    const result = adminActionError(apiError(status), action)

    expect(result).toMatchObject({ message, retryable, refresh })
    expect(result.message).not.toContain(LEAK)
  })

  it('explains a 403 as an account problem, not a report problem', () => {
    const result = adminActionError(apiError(403), 'resolve')

    expect(result.kind).toBe('forbidden')
    expect(result.message).toContain('administrator access')
    expect(result.message).not.toContain('report')
  })

  it('leaves a 401 to the login flow', () => {
    expect(adminActionError(apiError(401), 'suspendUser').kind).toBe('unauthorized')
  })
})

describe('resolution note', () => {
  it('allows 1,000 characters after trimming', () => {
    expect(noteLengthError(`  ${'n'.repeat(1000)}  `)).toBeUndefined()
    expect(noteLengthError('n'.repeat(1002))).toBe('The note can be at most 1,000 characters (2 too many).')
  })

  it('maps a backend field error on the note to its own wording', () => {
    expect(serverNoteError(apiError(400, [{ field: 'resolutionNote', message: 'size must be between 0 and 1000' }]), 'x')).toBe(
      'This note wasn’t accepted. Shorten or rephrase it.',
    )
    expect(serverNoteError(apiError(400), 'x')).toBeUndefined()
  })
})
