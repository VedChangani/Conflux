import type { ReportReason, ReportTargetType } from '../reports/types'

export const REPORT_STATUSES = ['OPEN', 'RESOLVED', 'DISMISSED'] as const
export type ReportStatus = (typeof REPORT_STATUSES)[number]

export const DEFAULT_REPORT_STATUS: ReportStatus = 'OPEN'

export const REPORT_PAGE_SIZES = [10, 20, 50] as const
export const DEFAULT_REPORT_PAGE_SIZE = 20
export const MAX_REPORT_PAGE_SIZE = 50

export const RESOLUTION_NOTE_MAX_LENGTH = 1_000

export interface ReportSummary {
  id: number
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  status: ReportStatus
  createdAt: string
  reviewedAt: string | null
}

export interface ReportPerson {
  id: number
  username: string
  displayName: string
}

export type AdminUserStatus = 'ACTIVE' | 'SUSPENDED'
export type AdminListingStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'SUSPENDED'

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

export interface ListingTarget {
  id: number
  slug: string
  title: string
  shortPitch: string
  description: string
  status: AdminListingStatus
  owner: ReportPerson | null
}

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

export type ReportDetail = ReportDetailBase &
  (
    | { targetType: 'USER'; target: UserTarget | null }
    | { targetType: 'LISTING'; target: ListingTarget | null }
    | { targetType: 'MESSAGE'; target: MessageTarget | null }
  )

export interface ReportDecisionRequest {
  resolutionNote: string | null
}

export type ReportDecision = 'resolve' | 'dismiss'

export type TargetAction = 'suspendUser' | 'restoreUser' | 'suspendListing' | 'restoreListing'

export type AdminAction = ReportDecision | TargetAction
