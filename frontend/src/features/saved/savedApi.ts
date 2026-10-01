import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { SavedListing } from './types'

/**
 * Saved listings of the signed-in user. The backend always acts on the token's user;
 * nothing here sends a user id. Save and unsave are idempotent (204 either way).
 */
export const savedApi = {
  save: (listingId: number) => apiClient.post<void>(`/listings/${listingId}/save`),
  unsave: (listingId: number) => apiClient.delete<void>(`/listings/${listingId}/save`),
  /** Zero-based `page`. Only listings that are still published are returned. */
  list: (page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<SavedListing>>(`/saved-listings?page=${page}&size=${size}`, { signal }),
}
