import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { ReportDecisionRequest, ReportDetail, ReportSummary, TargetAction } from './types'

const TARGET_PATHS: Record<TargetAction, (id: number) => string> = {
  suspendUser: (id) => `/admin/users/${id}/suspend`,
  restoreUser: (id) => `/admin/users/${id}/restore`,
  suspendListing: (id) => `/admin/listings/${id}/suspend`,
  restoreListing: (id) => `/admin/listings/${id}/restore`,
}

/**
 * Admin endpoints (`/admin/**`, ROLE_ADMIN). The acting administrator is always the token's
 * user; reviewer, review time and audit entries are set by the backend.
 */
export const adminApi = {
  /** `GET /admin/reports` with a query string built by `toReportsApiSearch`. Newest first. */
  reports: (apiSearch: string, signal?: AbortSignal) =>
    apiClient.get<PageResponse<ReportSummary>>(`/admin/reports?${apiSearch}`, { signal }),
  report: (id: number, signal?: AbortSignal) => apiClient.get<ReportDetail>(`/admin/reports/${id}`, { signal }),
  /** OPEN → RESOLVED. Resolves with the updated report; 409 unless OPEN. */
  resolve: (id: number, request: ReportDecisionRequest) =>
    apiClient.post<ReportDetail>(`/admin/reports/${id}/resolve`, request),
  /** OPEN → DISMISSED. Resolves with the updated report; 409 unless OPEN. */
  dismiss: (id: number, request: ReportDecisionRequest) =>
    apiClient.post<ReportDetail>(`/admin/reports/${id}/dismiss`, request),
  /**
   * Suspends or restores a user or listing. 204 with no body, also when nothing changed
   * (every operation is idempotent): read the report again for the target's new state.
   */
  moderate: (action: TargetAction, targetId: number) => apiClient.post<void>(TARGET_PATHS[action](targetId)),
}
