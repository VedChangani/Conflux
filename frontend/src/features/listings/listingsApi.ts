import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { ListingCard, ListingDetail, ListingRequest, MyListingSummary } from './types'

export const listingsApi = {
  discover: (apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<ListingCard>>(apiSearch ? `/listings?${apiSearch}` : '/listings', {
      auth: false,
      signal,
    }),
  bySlug: (slug: string, signal?: AbortSignal) =>
    apiClient.get<ListingDetail>(`/listings/${encodeURIComponent(slug)}`, { auth: false, signal }),
  mine: (page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<MyListingSummary>>(`/listings/mine?page=${page}&size=${size}`, { signal }),
  myListing: (id: number, signal?: AbortSignal) => apiClient.get<ListingDetail>(`/listings/mine/${id}`, { signal }),
  create: (request: ListingRequest) => apiClient.post<ListingDetail>('/listings', request),
  update: (id: number, request: ListingRequest) => apiClient.put<ListingDetail>(`/listings/${id}`, request),
  publish: (id: number) => apiClient.post<ListingDetail>(`/listings/${id}/publish`),
  archive: (id: number) => apiClient.delete<void>(`/listings/${id}`),
}
