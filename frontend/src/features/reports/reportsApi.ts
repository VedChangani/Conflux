import { apiClient } from '../../services/apiClient'
import type { ReportRequest, ReportResponse } from './types'

export const reportsApi = {
  create: (request: ReportRequest) => apiClient.post<ReportResponse>('/reports', request),
}
