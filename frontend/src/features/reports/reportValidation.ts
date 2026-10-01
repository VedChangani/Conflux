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

export function isValidTargetId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

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

export function toReportRequest(target: ReportTarget, reason: ReportReason, details: string): ReportRequest {
  return {
    targetType: target.type,
    targetId: target.id,
    reason,
    details: details.trim() || null,
  }
}

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
