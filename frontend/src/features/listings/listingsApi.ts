import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { ListingCard, ListingDetail } from './types'

/**
 * Public marketplace endpoints. They never send the stored token: nothing here depends
 * on who is looking, and a stale token must not stop anyone from browsing.
 */
export const listingsApi = {
  /** `GET /listings` with a query string built by `toApiSearch`. */
  discover: (apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<ListingCard>>(apiSearch ? `/listings?${apiSearch}` : '/listings', {
      auth: false,
      signal,
    }),
  /** `GET /listings/{slug}`: published listings only, 404 otherwise. */
  bySlug: (slug: string, signal?: AbortSignal) =>
    apiClient.get<ListingDetail>(`/listings/${encodeURIComponent(slug)}`, { auth: false, signal }),
}
