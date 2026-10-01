import { ApiError } from '../../services/apiClient'
import {
  REPORT_DETAILS_MAX_LENGTH,
  REPORT_REASONS,
  REPORT_TARGET_TYPES,
  type ReportReason,
  type ReportRequest,
  type ReportTarget,
} from './types'

const numberFormat = new Intl.NumberFormat('en-US')

export function isReportTargetType(value: unknown): value is ReportTarget['type'] {
  return typeof value === 'string' && (REPORT_TARGET_TYPES as readonly string[]).includes(value)
}

export function isReportReason(value: unknown): value is ReportReason {
  return typeof value === 'string' && (REPORT_REASONS as readonly string[]).includes(value)
}

/** A positive whole number the backend can address (`@Positive Long`). */
export function isValidTargetId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

/** Whether a target can be reported at all; otherwise no report action is offered. */
export function isReportableTarget(target: ReportTarget): boolean {
  return isReportTargetType(target.type) && isValidTargetId(target.id)
}

export interface ReportDraft {
  reason: ReportReason | ''
  details: string
}

export interface ReportFieldErrors {
  reason?: string
  details?: string
}

export function detailsError(details: string): string | undefined {
  const over = details.trim().length - REPORT_DETAILS_MAX_LENGTH
  return over > 0
    ? `Details can be at most ${numberFormat.format(REPORT_DETAILS_MAX_LENGTH)} characters (${numberFormat.format(over)} too many).`
    : undefined
}

/** The same rules as the backend, so a malformed request is never sent. */
export function validateReport(draft: ReportDraft): ReportFieldErrors {
  const errors: ReportFieldErrors = {}
  if (!isReportReason(draft.reason)) {
    errors.reason = 'Choose a reason.'
  }
  const details = detailsError(draft.details)
  if (details) {
    errors.details = details
  }
  return errors
}

/**
 * The complete `POST /reports` body, built field by field so nothing else (reporter, status,
 * reviewer, review dates or notes) can ever be sent. Details are trimmed; blank is `null`.
 * Call only with a reportable target and a valid draft.
 */
export function toReportRequest(target: ReportTarget, reason: ReportReason, details: string): ReportRequest {
  return {
    targetType: target.type,
    targetId: target.id,
    reason,
    details: details.trim() || null,
  }
}

/** Field errors from a backend 400, in the dialog's own words. */
export function serverReportErrors(error: unknown, draft: ReportDraft): ReportFieldErrors {
  const errors: ReportFieldErrors = {}
  if (error instanceof ApiError) {
    for (const { field } of error.fieldErrors) {
      if (field === 'reason') {
        errors.reason = 'Choose a reason.'
      } else if (field === 'details') {
        errors.details = detailsError(draft.details) ?? 'These details weren’t accepted. Shorten or rephrase them.'
      }
    }
  }
  return errors
}
