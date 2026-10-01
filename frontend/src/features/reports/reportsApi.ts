import { apiClient } from '../../services/apiClient'
import type { ReportRequest, ReportResponse } from './types'

/** Submitting reports. The reporter is always the token's user; the backend decides the rest. */
export const reportsApi = {
  /** 201 with the stored report. */
  create: (request: ReportRequest) => apiClient.post<ReportResponse>('/reports', request),
}
