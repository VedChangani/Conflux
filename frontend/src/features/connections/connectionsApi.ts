import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { Connection, ConnectionBox } from './types'

export const connectionsApi = {
  list: (box: ConnectionBox, apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<Connection>>(`/connections/${box}?${apiSearch}`, { signal }),
  detail: (id: number, signal?: AbortSignal) => apiClient.get<Connection>(`/connections/${id}`, { signal }),
  accept: (id: number) => apiClient.post<Connection>(`/connections/${id}/accept`),
  reject: (id: number) => apiClient.post<Connection>(`/connections/${id}/reject`),
  withdraw: (id: number) => apiClient.delete<void>(`/connections/${id}`),
  pendingReceivedCount: async (signal?: AbortSignal) =>
    (await connectionsApi.list('received', 'status=PENDING&page=0&size=1', signal)).totalElements,
}
