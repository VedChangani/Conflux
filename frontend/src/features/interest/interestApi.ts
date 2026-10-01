import { apiRequestWithStatus } from '../../services/apiClient'
import type { Connection, InterestResult } from './types'

export const interestApi = {
  express: async (listingId: number): Promise<InterestResult> => {
    const { status, data } = await apiRequestWithStatus<Connection>(`/listings/${listingId}/interest`, {
      method: 'POST',
    })
    return { connection: data, created: status === 201 }
  },
}
