import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { ListingCard, ListingDetail } from './types'

export const listingsApi = {
  discover: (apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<ListingCard>>(apiSearch ? `/listings?${apiSearch}` : '/listings', {
      auth: false,
      signal,
    }),
  bySlug: (slug: string, signal?: AbortSignal) =>
    apiClient.get<ListingDetail>(`/listings/${encodeURIComponent(slug)}`, { auth: false, signal }),
}
