import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { SavedListing } from './types'

export const savedApi = {
  save: (listingId: number) => apiClient.post<void>(`/listings/${listingId}/save`),
  unsave: (listingId: number) => apiClient.delete<void>(`/listings/${listingId}/save`),
  list: (page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<SavedListing>>(`/saved-listings?page=${page}&size=${size}`, { signal }),
}
