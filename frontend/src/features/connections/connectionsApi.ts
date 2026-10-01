import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { Connection, ConnectionBox } from './types'

/**
 * Connection endpoints. The backend acts for the token's user and decides who may do
 * what; nothing here sends a user id.
 */
export const connectionsApi = {
  /** `GET /connections/{sent|received}` with a query string built by `toConnectionsApiSearch`. */
  list: (box: ConnectionBox, apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<Connection>>(`/connections/${box}?${apiSearch}`, { signal }),
  /** 404 unless the user is the requester or the listing owner. */
  detail: (id: number, signal?: AbortSignal) => apiClient.get<Connection>(`/connections/${id}`, { signal }),
  /** Listing owner only. Resolves with the updated connection. */
  accept: (id: number) => apiClient.post<Connection>(`/connections/${id}/accept`),
  /** Listing owner only. Resolves with the updated connection. */
  reject: (id: number) => apiClient.post<Connection>(`/connections/${id}/reject`),
  /** Requester only. 204 with no body: read the connection again for its new state. */
  withdraw: (id: number) => apiClient.delete<void>(`/connections/${id}`),
  /** How many received requests await the user's answer. */
  pendingReceivedCount: async (signal?: AbortSignal) =>
    (await connectionsApi.list('received', 'status=PENDING&page=0&size=1', signal)).totalElements,
}
