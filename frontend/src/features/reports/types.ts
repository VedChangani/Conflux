export const REPORT_TARGET_TYPES = ['USER', 'LISTING', 'MESSAGE'] as const
export type ReportTargetType = (typeof REPORT_TARGET_TYPES)[number]

export const REPORT_REASONS = [
  'SPAM',
  'SCAM_OR_FRAUD',
  'HARASSMENT',
  'INAPPROPRIATE_CONTENT',
  'MISLEADING_INFORMATION',
  'OTHER',
] as const
export type ReportReason = (typeof REPORT_REASONS)[number]

export const REPORT_DETAILS_MAX_LENGTH = 1_000

export interface ReportTarget {
  type: ReportTargetType
  id: number
  description: string
}

export interface ReportRequest {
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  details: string | null
}

export interface ReportResponse {
  id: number
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  status: string
  createdAt: string
}
