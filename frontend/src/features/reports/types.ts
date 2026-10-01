/** What can be reported (`ReportTargetType`). */
export const REPORT_TARGET_TYPES = ['USER', 'LISTING', 'MESSAGE'] as const
export type ReportTargetType = (typeof REPORT_TARGET_TYPES)[number]

/** Why something is reported (`ReportReason`), in the order offered. */
export const REPORT_REASONS = [
  'SPAM',
  'SCAM_OR_FRAUD',
  'HARASSMENT',
  'INAPPROPRIATE_CONTENT',
  'MISLEADING_INFORMATION',
  'OTHER',
] as const
export type ReportReason = (typeof REPORT_REASONS)[number]

/** The backend's limit on details, after trimming (`Report.DETAILS_MAX_LENGTH`). */
export const REPORT_DETAILS_MAX_LENGTH = 1_000

/** The thing being reported, as the report flow needs it. */
export interface ReportTarget {
  type: ReportTargetType
  /** The backend id of the user, listing or message. Sent, never displayed. */
  id: number
  /** Human description shown in the dialog, e.g. `Alice Anders (@alice)`. */
  description: string
}

/**
 * `POST /reports` body: exactly these four fields. The reporter, status and review fields
 * are set by the server and never sent.
 */
export interface ReportRequest {
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  /** Trimmed; `null` when blank. */
  details: string | null
}

/** `ReportResponse`: the stored report, without reporter or moderation data. */
export interface ReportResponse {
  id: number
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  status: string
  createdAt: string
}
