import { describe, expect, it } from 'vitest'
import { json, mockApi, problem } from '../test/api'
import { apiRequestWithStatus } from './apiClient'

describe('apiRequestWithStatus', () => {
  it.each([201, 200])('resolves with the %i status and the parsed body', async (status) => {
    mockApi({ 'POST /things': () => json({ id: 1 }, status) })

    await expect(apiRequestWithStatus('/things', { method: 'POST' })).resolves.toEqual({ status, data: { id: 1 } })
  })

  it('rejects failures with an ApiError like apiRequest', async () => {
    mockApi({ 'POST /things': () => problem(409, 'Conflict.') })

    await expect(apiRequestWithStatus('/things', { method: 'POST' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
    })
  })
})
