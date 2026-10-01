import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { ReportDecisionRequest, ReportDetail, ReportSummary, TargetAction } from './types'

const TARGET_PATHS: Record<TargetAction, (id: number) => string> = {
  suspendUser: (id) => `/admin/users/${id}/suspend`,
  restoreUser: (id) => `/admin/users/${id}/restore`,
  suspendListing: (id) => `/admin/listings/${id}/suspend`,
  restoreListing: (id) => `/admin/listings/${id}/restore`,
}

export const adminApi = {
  reports: (apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<ReportSummary>>(`/admin/reports?${apiSearch}`, { signal }),
  report: (id: number, signal?: AbortSignal) => apiClient.get<ReportDetail>(`/admin/reports/${id}`, { signal }),
  resolve: (id: number, request: ReportDecisionRequest) =>
    apiClient.post<ReportDetail>(`/admin/reports/${id}/resolve`, request),
  dismiss: (id: number, request: ReportDecisionRequest) =>
    apiClient.post<ReportDetail>(`/admin/reports/${id}/dismiss`, request),
  moderate: (action: TargetAction, targetId: number) => apiClient.post<void>(TARGET_PATHS[action](targetId)),
}
