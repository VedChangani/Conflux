import type { ReportReason, ReportTargetType } from '../reports/types'

/** Review state of a report (`ReportStatus`): OPEN → RESOLVED | DISMISSED; both are final. */
export const REPORT_STATUSES = ['OPEN', 'RESOLVED', 'DISMISSED'] as const
export type ReportStatus = (typeof REPORT_STATUSES)[number]

/** What the queue shows when no status is chosen; the backend uses the same default. */
export const DEFAULT_REPORT_STATUS: ReportStatus = 'OPEN'

/** The queue's page sizes: the backend accepts 1–50 and defaults to 20. */
export const REPORT_PAGE_SIZES = [10, 20, 50] as const
export const DEFAULT_REPORT_PAGE_SIZE = 20
export const MAX_REPORT_PAGE_SIZE = 50

/** The backend's limit on the optional note, after trimming (`Report.RESOLUTION_NOTE_MAX_LENGTH`). */
export const RESOLUTION_NOTE_MAX_LENGTH = 1_000

/** `ReportSummaryResponse`: a queue entry. No details, notes, people or target content. */
export interface ReportSummary {
  id: number
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  status: ReportStatus
  createdAt: string
  reviewedAt: string | null
}

/** `ReportDetailResponse.Person`: a public summary, never an email. */
export interface ReportPerson {
  id: number
  username: string
  displayName: string
}

export type AdminUserStatus = 'ACTIVE' | 'SUSPENDED'
export type AdminListingStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'SUSPENDED'

/** `ReportDetailResponse.UserTarget`. */
export interface UserTarget {
  id: number
  username: string
  displayName: string
  bio: string | null
  location: string | null
  websiteUrl: string | null
  githubUrl: string | null
  linkedinUrl: string | null
  status: AdminUserStatus
}

/** `ReportDetailResponse.ListingTarget`. */
export interface ListingTarget {
  id: number
  slug: string
  title: string
  shortPitch: string
  description: string
  status: AdminListingStatus
  owner: ReportPerson | null
}

/** `ReportDetailResponse.MessageTarget`. */
export interface MessageTarget {
  id: number
  content: string
  createdAt: string
  sender: ReportPerson | null
  conversationId: number
  listing: { id: number; slug: string; title: string }
}

interface ReportDetailBase {
  id: number
  targetId: number
  reason: ReportReason
  details: string | null
  status: ReportStatus
  createdAt: string
  reviewedAt: string | null
  resolutionNote: string | null
  reporter: ReportPerson | null
  reviewer: ReportPerson | null
}

/**
 * `ReportDetailResponse`: the admin view of one report. The target's shape follows
 * `targetType`; it is `null` when the target no longer exists.
 */
export type ReportDetail = ReportDetailBase &
  (
    | { targetType: 'USER'; target: UserTarget | null }
    | { targetType: 'LISTING'; target: ListingTarget | null }
    | { targetType: 'MESSAGE'; target: MessageTarget | null }
  )

/** `ReportDecisionRequest`: the optional note, trimmed; blank is `null`. */
export interface ReportDecisionRequest {
  resolutionNote: string | null
}

export type ReportDecision = 'resolve' | 'dismiss'

export type TargetAction = 'suspendUser' | 'restoreUser' | 'suspendListing' | 'restoreListing'

export type AdminAction = ReportDecision | TargetAction
