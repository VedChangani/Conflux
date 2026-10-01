import { labelOf } from '../listings/labels'
import { REASON_COPY } from '../reports/reasons'
import type { ReportTargetType } from '../reports/types'
import type { AdminListingStatus, AdminUserStatus, ReportStatus } from './types'

export const REPORT_STATUS_LABELS: Record<ReportStatus, string> = {
  OPEN: 'Open',
  RESOLVED: 'Resolved',
  DISMISSED: 'Dismissed',
}

export const TARGET_TYPE_LABELS: Record<ReportTargetType, string> = {
  USER: 'User',
  LISTING: 'Listing',
  MESSAGE: 'Message',
}

export const USER_STATUS_LABELS: Record<AdminUserStatus, string> = {
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
}

export const LISTING_STATUS_LABELS: Record<AdminListingStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
  SUSPENDED: 'Suspended',
}

const REASON_LABELS: Record<string, string> = Object.fromEntries(
  Object.entries(REASON_COPY).map(([reason, copy]) => [reason, copy.label]),
)

export const reportStatusLabel = (status: string) => labelOf(REPORT_STATUS_LABELS, status)
export const targetTypeLabel = (type: string) => labelOf(TARGET_TYPE_LABELS, type)
export const reasonLabel = (reason: string) => labelOf(REASON_LABELS, reason)
export const userStatusLabel = (status: string) => labelOf(USER_STATUS_LABELS, status)
export const listingStatusLabel = (status: string) => labelOf(LISTING_STATUS_LABELS, status)
