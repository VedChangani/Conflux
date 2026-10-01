import { apiRequestWithStatus } from '../../services/apiClient'
import type { Connection, InterestResult } from './types'

/** Expressing interest in a listing. The backend acts for the token's user; no user id is sent. */
export const interestApi = {
  /** 201 with a new PENDING request, or 200 with the existing PENDING or ACCEPTED one. */
  express: async (listingId: number): Promise<InterestResult> => {
    const { status, data } = await apiRequestWithStatus<Connection>(`/listings/${listingId}/interest`, {
      method: 'POST',
    })
    return { connection: data, created: status === 201 }
  },
}
